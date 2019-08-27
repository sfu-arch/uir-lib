package dnn_layers


import chisel3._
import chisel3.iotesters.PeekPokeTester
import config._
import node._
import org.scalatest.{FlatSpec, Matchers}
import FPU._
//import dnn.DotNode

// Tester.
class DotCompTests(df: Mac[matNxN])
                  (implicit p: config.Parameters) extends PeekPokeTester(df) {
  poke(df.io.enable.valid, true)
  poke(df.io.enable.bits.control, true)

  poke(df.io.LeftIO.bits.data, 0xFEFEFEFEL)
  poke(df.io.LeftIO.valid, true)
  poke(df.io.LeftIO.bits.predicate, true)


  poke(df.io.RightIO.bits.data, 0x04020402L)
  poke(df.io.RightIO.valid, true)
  poke(df.io.RightIO.bits.predicate, true)

  poke(df.io.Out(0).ready, true.B)
  step(20)
}


class FXDotCompTests(df: Mac[FXmatNxN])
                    (implicit p: config.Parameters) extends PeekPokeTester(df) {
  poke(df.io.enable.valid, true)
  poke(df.io.enable.bits.control, true)
  // 0x32 0011.0010 . Fixed point 3.125 in fixed point 4 BP.
  poke(df.io.LeftIO.bits.data, 0x49494949L)
  poke(df.io.LeftIO.valid, true)
  poke(df.io.LeftIO.bits.predicate, true)

  // 0x32 (3.125) * 0x20 (2.0) = 6.25 (0x64 or 100)
  poke(df.io.RightIO.bits.data, 0x40L)
  poke(df.io.RightIO.valid, true)
  poke(df.io.RightIO.bits.predicate, true)

  poke(df.io.Out(0).ready, true.B)
  step(20)
}

class FPDotCompTests(df: Mac[FPmatNxN])
                    (implicit p: config.Parameters) extends PeekPokeTester(df) {
  poke(df.io.enable.valid, true)
  poke(df.io.enable.bits.control, true)
  // 0x49 = 3.125 (Mini 8 bit format. 3 bit exp, 5 bit mantissa
  poke(df.io.LeftIO.bits.data, 0x49494949L)
  poke(df.io.LeftIO.valid, true)
  poke(df.io.LeftIO.bits.predicate, true)

  // 0x4e - 3.7 . Result : 103.
  poke(df.io.RightIO.bits.data, 0x4e4e4e4eL)
  poke(df.io.RightIO.valid, true)
  poke(df.io.RightIO.bits.predicate, true)

  poke(df.io.Out(0).ready, true.B)
  step(20)
}


class DotCompTester extends FlatSpec with Matchers {
  implicit val p = config.Parameters.root((new Mat_VecConfig).toInstance)
  it should "Typ Compute Tester" in {
    //    chisel3.iotesters.Driver.execute(Array("--backend-name", "verilator", "--target-dir", "test_run_dir"),
    //      () => new DotNode(NumOuts = 1, ID = 0, 4, "Add")(new matNxN(2, true))) {
    //      c => new DotCompTests(c)
    //    } should be(true)

    chisel3.iotesters.Driver.execute(Array("--backend-name", "verilator", "--target-dir", "test_run_dir"),
      () => new Mac(NumOuts = 1, ID = 0, 4, "Mul")(new FXmatNxN(2,4))) {
      c => new FXDotCompTests(c)
    } should be(true)
    //    chisel3.iotesters.Driver.execute(Array("--backend-name", "verilator", "--target-dir", "test_run_dir"),
    //      () => new DotNode(NumOuts = 1, ID = 0, 4, "Mul")(new FPmatNxN(2, t = FType.M))) {
    //      c => new FPDotCompTests(c)
    //    } should be(true)
  }
}
