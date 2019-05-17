package dataflow


import java.io.PrintWriter
import java.io.File
import chisel3._
import chisel3.util._
import chisel3.Module
import chisel3.testers._
import chisel3.iotesters._
import org.scalatest.{FlatSpec, Matchers}
import muxes._
import config._
import control._
import util._
import interfaces._
import regfile._
import memory._
import stack._
import arbiters._
import loop._
import accel._
import node._
import scala.util.Random


class stencilMainIO(implicit val p: Parameters) extends Module with CoreParams with CacheParams {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new Call(List(32, 32))))
    val req = Flipped(Decoupled(new MemReq))
    val resp = Output(Valid(new MemResp))
    val out = Decoupled(new Call(List()))
  })

  def cloneType = new stencilMainIO().asInstanceOf[this.type]
}


class stencilDirect()(implicit p: Parameters) extends stencilMainIO {

  val cache = Module(new Cache) // Simple Nasti Cache
  val memModel = Module(new NastiMemSlave) // Model of DRAM to connect to Cache

  // Connect the wrapper I/O to the memory model initialization interface so the
  // test bench can write contents at start.
  memModel.io.nasti <> cache.io.nasti
  memModel.io.init.bits.addr := 0.U
  memModel.io.init.bits.data := 0.U
  memModel.io.init.valid := false.B
  cache.io.cpu.abort := false.B

  // Wire up the cache, TM, and modules under test.
  val stencil = Module(new stencilDF())
  val stencil_detach1 = Module(new stencil_detach1DF())
  val stencil_inner = Module(new stencil_innerDF())

  stencil.io.MemReq <> DontCare
  stencil.io.MemResp <> DontCare

  val MemArbiter = Module(new MemArbiter(3))

  MemArbiter.io.cpu.MemReq(0) <> stencil_inner.io.MemReq
  stencil_inner.io.MemResp <> MemArbiter.io.cpu.MemResp(0)

  MemArbiter.io.cpu.MemReq(1) <> stencil_detach1.io.MemReq
  stencil_detach1.io.MemResp <> MemArbiter.io.cpu.MemResp(1)

  MemArbiter.io.cpu.MemReq(2) <> io.req
  io.resp <> MemArbiter.io.cpu.MemResp(2)

  cache.io.cpu.req <> MemArbiter.io.cache.MemReq
  MemArbiter.io.cache.MemResp <> cache.io.cpu.resp

  // tester to cilk_for_test02
  stencil.io.in <> io.in

  // cilk_for_test02 to task controller
  stencil_detach1.io.in <> stencil.io.call_8_out
  stencil.io.call_8_in <> stencil_detach1.io.out

  stencil_inner.io.in <> stencil_detach1.io.call_4_out
  stencil_detach1.io.call_4_in <> stencil_inner.io.out

  // cilk_for_test02 to tester
  io.out <> stencil.io.out

}


class stencilMainTM(tiles: Int)(implicit p: Parameters) extends stencilMainIO {

  val cache = Module(new Cache) // Simple Nasti Cache
  val memModel = Module(new NastiMemSlave(latency = 80)) // Model of DRAM to connect to Cache

  // Connect the wrapper I/O to the memory model initialization interface so the
  // test bench can write contents at start.
  memModel.io.nasti <> cache.io.nasti
  memModel.io.init.bits.addr := 0.U
  memModel.io.init.bits.data := 0.U
  memModel.io.init.valid := false.B
  cache.io.cpu.abort := false.B

  // Wire up the cache, TM, and modules under test.
  val children = tiles
  val TaskControllerModule = Module(new TaskController(List(32, 32, 32), List(), 1, children))
  val stencil = Module(new stencilDF())

  stencil.io.MemReq <> DontCare
  stencil.io.MemResp <> DontCare

  val stencil_detach1 = for (i <- 0 until children) yield {
    val detach1 = Module(new stencil_detach1DF())
    detach1
  }
  val stencil_inner = for (i <- 0 until children) yield {
    val inner = Module(new stencil_innerDF())
    inner
  }

  val MemArbiter = Module(new MemArbiter((2 * children) + 1))
  for (i <- 0 until children) {
    MemArbiter.io.cpu.MemReq(i) <> stencil_inner(i).io.MemReq
    stencil_inner(i).io.MemResp <> MemArbiter.io.cpu.MemResp(i)

    MemArbiter.io.cpu.MemReq(children + i) <> stencil_detach1(i).io.MemReq
    stencil_detach1(i).io.MemResp <> MemArbiter.io.cpu.MemResp(children + i)
  }

  MemArbiter.io.cpu.MemReq(2 * children) <> io.req
  io.resp <> MemArbiter.io.cpu.MemResp(2 * children)

  cache.io.cpu.req <> MemArbiter.io.cache.MemReq
  MemArbiter.io.cache.MemResp <> cache.io.cpu.resp

  // tester to cilk_for_test02
  stencil.io.in <> io.in

  // cilk_for_test02 to task controller
  TaskControllerModule.io.parentIn(0) <> stencil.io.call_8_out

  // task controller to sub-task stencil_detach1
  for (i <- 0 until children) {
    stencil_detach1(i).io.in <> TaskControllerModule.io.childOut(i)
    stencil_inner(i).io.in <> stencil_detach1(i).io.call_4_out
    stencil_detach1(i).io.call_4_in <> stencil_inner(i).io.out
    TaskControllerModule.io.childIn(i) <> stencil_detach1(i).io.out
  }

  // Task controller to cilk_for_test02
  stencil.io.call_8_in <> TaskControllerModule.io.parentOut(0)

  // cilk_for_test02 to tester
  io.out <> stencil.io.out

}

class stencilTest01[T <: stencilMainIO](c: T, tiles: Int) extends PeekPokeTester(c) {

  def MemRead(addr: Int): BigInt = {
    while (peek(c.io.req.ready) == 0) {
      step(1)
    }
    poke(c.io.req.valid, 1)
    poke(c.io.req.bits.addr, addr)
    poke(c.io.req.bits.iswrite, 0)
    poke(c.io.req.bits.tag, 0)
    poke(c.io.req.bits.mask, 0)
    poke(c.io.req.bits.mask, -1)
    step(1)
    while (peek(c.io.resp.valid) == 0) {
      step(1)
    }
    val result = peek(c.io.resp.bits.data)
    result
  }

  def MemWrite(addr: Int, data: Int): BigInt = {
    while (peek(c.io.req.ready) == 0) {
      step(1)
    }
    poke(c.io.req.valid, 1)
    poke(c.io.req.bits.addr, addr)
    poke(c.io.req.bits.data, data)
    poke(c.io.req.bits.iswrite, 1)
    poke(c.io.req.bits.tag, 0)
    poke(c.io.req.bits.mask, 0)
    poke(c.io.req.bits.mask, -1)
    step(1)
    poke(c.io.req.valid, 0)
    1
  }


  def dumpMemory(path: String) = {
    //Writing mem states back to the file
    val pw = new PrintWriter(new File(path))
    for (i <- 0 until outDataVec.length) {
      val data = MemRead(outAddrVec(i))
      pw.write("0X" + outAddrVec(i).toHexString + " -> " + data + "\n")
    }
    pw.close

  }


  def dumpMemoryInit(path: String) = {
    //Writing mem states back to the file
    val pw = new PrintWriter(new File(path))
    for (i <- 0 until inDataVec.length) {
      val data = MemRead(inAddrVec(i))
      pw.write("0X" + inAddrVec(i).toHexString + " -> " + data + "\n")
    }
    for (i <- 0 until inDataVec.length) {
      val data = 0
      pw.write("0X" + outAddrVec(i).toHexString + " -> " + data + "\n")
    }
    pw.close

  }


  val inDataVec = List(
    3, 6, 7, 5,
    3, 5, 6, 2,
    9, 1, 2, 7,
    0, 9, 3, 6)
  val inAddrVec = List.range(0, 4 * 16, 4)

  val outAddrVec = List.range(256, 256 + (4 * 16), 4)

  val outAddVec = List(
    26, 39, 40, 29,
    36, 51, 50, 38,
    36, 47, 50, 35,
    28, 33, 37, 27
  )

  val outDataVec = outAddVec.map( e => math.floor((e)/9).toInt)



  // Write initial contents to the memory model.
  for (i <- 0 until inDataVec.length) {
    MemWrite(inAddrVec(i), inDataVec(i))
  }
//  for (i <- 0 until inDataVec.length) {
//    MemWrite(outAddrVec(i),0)
//  }
//  step(10)
//
//  dumpMemoryInit("init.mem")

  step(1)


  // Initializing the signals
  poke(c.io.in.bits.enable.control, false.B)
  poke(c.io.in.bits.enable.taskID, 0)
  poke(c.io.in.valid, false.B)
  poke(c.io.in.bits.data("field0").data, 0)
  poke(c.io.in.bits.data("field0").taskID, 0)
  poke(c.io.in.bits.data("field0").predicate, false.B)
  poke(c.io.in.bits.data("field1").data, 0)
  poke(c.io.in.bits.data("field1").taskID, 0)
  poke(c.io.in.bits.data("field1").predicate, false.B)
  poke(c.io.out.ready, false.B)
  step(1)
  poke(c.io.in.bits.enable.control, true.B)
  poke(c.io.in.valid, true.B)
  poke(c.io.in.bits.data("field0").data, 0) // Array a[] base address
  poke(c.io.in.bits.data("field0").predicate, true.B)
  poke(c.io.in.bits.data("field1").data, 256) // Array b[] base address
  poke(c.io.in.bits.data("field1").predicate, true.B)
  poke(c.io.out.ready, true.B)
  step(1)
  poke(c.io.in.bits.enable.control, false.B)
  poke(c.io.in.valid, false.B)
  poke(c.io.in.bits.data("field0").data, 0)
  poke(c.io.in.bits.data("field0").predicate, false.B)
  poke(c.io.in.bits.data("field1").data, 0.U)
  poke(c.io.in.bits.data("field1").predicate, false.B)

  step(1)

  var time = 0
  var result = false
  while (time < 20000) {
    time += 1
    step(1)
    if (peek(c.io.out.valid) == 1) {
      result = true
      println(Console.BLUE + s"*** Result returned for t=$tiles. Run time: $time cycles." + Console.RESET)
    }
  }

  //  Peek into the CopyMem to see if the expected data is written back to the Cache
  var valid_data = true
  for (i <- 0 until outDataVec.length) {
    val data = MemRead(outAddrVec(i))
    if (data != outDataVec(i).toInt) {
      println(Console.RED + s"[LOG] MEM[${outAddrVec(i).toInt}] :: $data. Hoping for \t ${outDataVec(i).toInt}" + Console.RESET)
      //println(Console.RED + s"*** Incorrect data received. Got $data. Hoping for ${outDataVec(i).toInt}" + Console.RESET)
      //println(Console.RED + s"*** Incorrect data received. Got $data. Hoping for ${outDataVec(i).toInt}" + Console.RESET)
      fail
      valid_data = false
    }
    else {
      println(Console.BLUE + s"[LOG] MEM[${outAddrVec(i).toInt}] :: $data" + Console.RESET)
    }
  }
  if (valid_data) {
    println(Console.BLUE + "*** Correct data written back." + Console.RESET)
  }


  if (!result) {
    println(Console.RED + "*** Timeout." + Console.RESET)
    fail
  }
}

class stencilTester1 extends FlatSpec with Matchers {
  implicit val p = config.Parameters.root((new MiniConfig).toInstance)
  val testParams = p.alterPartial({
    case TLEN => 8
    case TRACE => false
  })
  // iotester flags:
  // -ll  = log level <Error|Warn|Info|Debug|Trace>
  // -tbn = backend <firrtl|verilator|vcs>
  // -td  = target directory
  // -tts = seed for RNG
  it should s"Test: Direct connection" in {
    chisel3.iotesters.Driver.execute(
      Array(
        "-ll", "Error",
        "-tbn", "verilator",
        "-td", s"test_run_dir/stencil_direct",
        "-tts", "0001"),
      () => new stencilDirect()(testParams)) {
      c => new stencilTest01(c, 0)
    } should be(true)
  }
}


class stencilTest02[T <: stencilMainIO](c: T, tiles: Int) extends PeekPokeTester(c) {

  def MemRead(addr: Int): BigInt = {
    while (peek(c.io.req.ready) == 0) {
      step(1)
    }
    poke(c.io.req.valid, 1)
    poke(c.io.req.bits.addr, addr)
    poke(c.io.req.bits.iswrite, 0)
    poke(c.io.req.bits.tag, 0)
    poke(c.io.req.bits.mask, 0)
    poke(c.io.req.bits.mask, -1)
    step(1)
    while (peek(c.io.resp.valid) == 0) {
      step(1)
    }
    val result = peek(c.io.resp.bits.data)
    result
  }

  def MemWrite(addr: Int, data: Int): BigInt = {
    while (peek(c.io.req.ready) == 0) {
      step(1)
    }
    poke(c.io.req.valid, 1)
    poke(c.io.req.bits.addr, addr)
    poke(c.io.req.bits.data, data)
    poke(c.io.req.bits.iswrite, 1)
    poke(c.io.req.bits.tag, 0)
    poke(c.io.req.bits.mask, 0)
    poke(c.io.req.bits.mask, -1)
    step(1)
    poke(c.io.req.valid, 0)
    1
  }


  def dumpMemory(path: String) = {
    //Writing mem states back to the file
    val pw = new PrintWriter(new File(path))
    for (i <- 0 until outDataVec.length) {
      val data = MemRead(outAddrVec(i))
      pw.write("0X" + outAddrVec(i).toHexString + " -> " + data + "\n")
    }
    pw.close

  }


  def dumpMemoryInit(path: String) = {
    //Writing mem states back to the file
    val pw = new PrintWriter(new File(path))
    for (i <- 0 until inDataVec.length) {
      val data = MemRead(inAddrVec(i))
      pw.write("0X" + inAddrVec(i).toHexString + " -> " + data + "\n")
    }
    for (i <- 0 until inDataVec.length) {
      val data = 0
      pw.write("0X" + outAddrVec(i).toHexString + " -> " + data + "\n")
    }
    pw.close

  }


//  val inDataVec = List(
//    3, 6, 7, 5,
//    3, 5, 6, 2,
//    9, 1, 2, 7,
//    0, 9, 3, 6)

  val dataSize = 16
  val inDataVec = Seq.fill(dataSize * dataSize)(Random.nextInt)
  val inAddrVec = List.range(0, 4 * (dataSize * dataSize), 4)

  val outAddrVec = List.range(256, 256 + (4 * dataSize * dataSize), 4)

  val outAddVec = List(
    26, 39, 40, 29,
    36, 51, 50, 38,
    36, 47, 50, 35,
    28, 33, 37, 27
  )

  val outDataVec = outAddVec.map( e => math.floor((e)/9).toInt)



  // Write initial contents to the memory model.
  for (i <- 0 until inDataVec.length) {
    MemWrite(inAddrVec(i), inDataVec(i))
  }
//  for (i <- 0 until inDataVec.length) {
//    MemWrite(outAddrVec(i),0)
//  }
//  step(10)

//  dumpMemoryInit("init.mem")

  step(1)


  // Initializing the signals
  poke(c.io.in.bits.enable.control, false.B)
  poke(c.io.in.bits.enable.taskID, 0)
  poke(c.io.in.valid, false.B)
  poke(c.io.in.bits.data("field0").data, 0)
  poke(c.io.in.bits.data("field0").taskID, 0)
  poke(c.io.in.bits.data("field0").predicate, false.B)
  poke(c.io.in.bits.data("field1").data, 0)
  poke(c.io.in.bits.data("field1").taskID, 0)
  poke(c.io.in.bits.data("field1").predicate, false.B)
  poke(c.io.out.ready, false.B)
  step(1)
  poke(c.io.in.bits.enable.control, true.B)
  poke(c.io.in.valid, true.B)
  poke(c.io.in.bits.data("field0").data, 0) // Array a[] base address
  poke(c.io.in.bits.data("field0").predicate, true.B)
  poke(c.io.in.bits.data("field1").data, dataSize * dataSize) // Array b[] base address
  poke(c.io.in.bits.data("field1").predicate, true.B)
  poke(c.io.out.ready, true.B)
  step(1)
  poke(c.io.in.bits.enable.control, false.B)
  poke(c.io.in.valid, false.B)
  poke(c.io.in.bits.data("field0").data, 0)
  poke(c.io.in.bits.data("field0").predicate, false.B)
  poke(c.io.in.bits.data("field1").data, 0.U)
  poke(c.io.in.bits.data("field1").predicate, false.B)

  step(1)

  var time = 0
  var result = false
  while (time < 10000) {
    time += 1
    step(1)
    if (peek(c.io.out.valid) == 1) {
      result = true
      println(Console.BLUE + s"*** Result returned for t=$tiles. Run time: $time cycles." + Console.RESET)
    }
  }

  //  Peek into the CopyMem to see if the expected data is written back to the Cache
//  var valid_data = true
//  for (i <- 0 until outDataVec.length) {
//    val data = MemRead(outAddrVec(i))
//    if (data != outDataVec(i).toInt) {
//      println(Console.RED + s"[LOG] MEM[${outAddrVec(i).toInt}] :: $data. Hoping for \t ${outDataVec(i).toInt}" + Console.RESET)
//      //println(Console.RED + s"*** Incorrect data received. Got $data. Hoping for ${outDataVec(i).toInt}" + Console.RESET)
//      //println(Console.RED + s"*** Incorrect data received. Got $data. Hoping for ${outDataVec(i).toInt}" + Console.RESET)
//      fail
//      valid_data = false
//    }
//    else {
//      println(Console.BLUE + s"[LOG] MEM[${outAddrVec(i).toInt}] :: $data" + Console.RESET)
//    }
//  }
//  if (valid_data) {
//    println(Console.BLUE + "*** Correct data written back." + Console.RESET)
//  }


  if (!result) {
    println(Console.RED + "*** Timeout." + Console.RESET)
    fail
  }
}



class stencilTester2 extends FlatSpec with Matchers {
  implicit val p = config.Parameters.root((new MiniConfig).toInstance)
  val testParams = p.alterPartial({
    case TLEN => 8
    case TRACE => true
  })
  // iotester flags:
  // -ll  = log level <Error|Warn|Info|Debug|Trace>
  // -tbn = backend <firrtl|verilator|vcs>
  // -td  = target directory
  // -tts = seed for RNG
  //  val tile_list = List(1,2,4,8)
  val tile_list = List(1)
  for (tile <- tile_list) {
    it should s"Test: $tile tiles" in {
      chisel3.iotesters.Driver.execute(
        Array(
          "-ll", "Error",
          "-tbn", "verilator",
          "-td", s"test_run_dir/stencil_${tile}",
          "-tts", "0001"),
        () => new stencilMainTM(tile)(testParams)) {
        c => new stencilTest02(c, tile)
      } should be(true)
    }
  }
}
