package dataflow

import FPU._
import accel._
import arbiters._
import chisel3._
import chisel3.util._
import chisel3.Module._
import chisel3.testers._
import chisel3.iotesters._
import config._
import control._
import interfaces._
import junctions._
import loop._
import memory._
import muxes._
import node._
import org.scalatest._
import regfile._
import stack._
import util._


  /* ================================================================== *
   *                   PRINTING PORTS DEFINITION                        *
   * ================================================================== */

abstract class test05DFIO(implicit val p: Parameters) extends Module with CoreParams {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new Call(List(32))))
    val MemResp = Flipped(Valid(new MemResp))
    val MemReq = Decoupled(new MemReq)
    val out = Decoupled(new Call(List(32)))
  })
}

class test05DF(implicit p: Parameters) extends test05DFIO()(p) {


  /* ================================================================== *
   *                   PRINTING MEMORY MODULES                          *
   * ================================================================== */

  val MemCtrl = Module(new UnifiedController(ID = 0, Size = 32, NReads = 2, NWrites = 1)
  (WControl = new WriteMemoryController(NumOps = 1, BaseSize = 2, NumEntries = 2))
  (RControl = new ReadMemoryController(NumOps = 2, BaseSize = 2, NumEntries = 2))
  (RWArbiter = new ReadWriteArbiter()))

  io.MemReq <> MemCtrl.io.MemReq
  MemCtrl.io.MemResp <> io.MemResp

  val InputSplitter = Module(new SplitCallNew(List(2)))
  InputSplitter.io.In <> io.in



  /* ================================================================== *
   *                   PRINTING LOOP HEADERS                            *
   * ================================================================== */

  val Loop_0 = Module(new LoopBlockNode(NumIns = List(2), NumOuts = List(), NumCarry = List(1), NumExits = 1, ID = 0))



  /* ================================================================== *
   *                   PRINTING BASICBLOCK NODES                        *
   * ================================================================== */

  val bb_0 = Module(new BasicBlockNoMaskFastNode(NumInputs = 1, NumOuts = 1, BID = 0))

  val bb_1 = Module(new BasicBlockNoMaskFastNode(NumInputs = 1, NumOuts = 4, BID = 1))

  val bb_2 = Module(new BasicBlockNode(NumInputs = 2, NumOuts = 15, NumPhi = 1, BID = 2))



  /* ================================================================== *
   *                   PRINTING INSTRUCTION NODES                       *
   * ================================================================== */

  //  br label %5, !UID !3, !BB_UID !4
  val br_0 = Module(new UBranchNode(ID = 0))

  //  %3 = getelementptr inbounds i32, i32* %0, i32 9, !UID !5
  val Gep_1 = Module(new GepNode(NumIns = 1, NumOuts = 1, ID = 1)(ElementSize = 4, ArraySize = List()))

  //  %4 = load i32, i32* %3, align 4, !tbaa !6, !UID !10
  val ld_2 = Module(new UnTypLoad(NumPredOps = 0, NumSuccOps = 0, NumOuts = 1, ID = 2, RouteID = 0))

  //  ret i32 %4, !UID !11, !BB_UID !12
  val ret_3 = Module(new RetNode2(retTypes = List(32), ID = 3))

  //  %6 = phi i32 [ 0, %1 ], [ %12, %5 ], !UID !13
  val phi4 = Module(new PhiFastNode(NumInputs = 2, NumOutputs = 3, ID = 4, Res = true))

  //  %7 = getelementptr inbounds i32, i32* %0, i32 %6, !UID !14
  val Gep_5 = Module(new GepNode(NumIns = 1, NumOuts = 1, ID = 5)(ElementSize = 4, ArraySize = List()))

  //  %8 = load i32, i32* %7, align 4, !tbaa !6, !UID !15
  val ld_6 = Module(new UnTypLoad(NumPredOps = 0, NumSuccOps = 0, NumOuts = 1, ID = 6, RouteID = 1))

  //  %9 = shl i32 %8, 1, !UID !16
  val binaryOp_7 = Module(new ComputeNode(NumOuts = 1, ID = 7, opCode = "shl")(sign = false))

  //  %10 = add nuw nsw i32 %6, 5, !UID !17
  val binaryOp_8 = Module(new ComputeNode(NumOuts = 1, ID = 8, opCode = "add")(sign = false))

  //  %11 = getelementptr inbounds i32, i32* %0, i32 %10, !UID !18
  val Gep_9 = Module(new GepNode(NumIns = 1, NumOuts = 1, ID = 9)(ElementSize = 4, ArraySize = List()))

  //  store i32 %9, i32* %11, align 4, !tbaa !6, !UID !19
  val st_10 = Module(new UnTypStore(NumPredOps = 0, NumSuccOps = 0, ID = 10, RouteID = 0))

  //  %12 = add nuw nsw i32 %6, 1, !UID !20
  val binaryOp_11 = Module(new ComputeNode(NumOuts = 2, ID = 11, opCode = "add")(sign = false))

  //  %13 = icmp eq i32 %12, 5, !UID !21
  val icmp_12 = Module(new IcmpNode(NumOuts = 1, ID = 12, opCode = "eq")(sign = false))

  //  br i1 %13, label %2, label %5, !UID !22, !BB_UID !23
  val br_13 = Module(new CBranchNodeVariable(NumTrue = 1, NumFalse = 1, NumPredecessor = 0, ID = 13))



  /* ================================================================== *
   *                   PRINTING CONSTANTS NODES                         *
   * ================================================================== */

  //i32 9
  val const0 = Module(new ConstFastNode(value = 9, ID = 0))

  //i32 0
  val const1 = Module(new ConstFastNode(value = 0, ID = 1))

  //i32 1
  val const2 = Module(new ConstFastNode(value = 1, ID = 2))

  //i32 5
  val const3 = Module(new ConstFastNode(value = 5, ID = 3))

  //i32 1
  val const4 = Module(new ConstFastNode(value = 1, ID = 4))

  //i32 5
  val const5 = Module(new ConstFastNode(value = 5, ID = 5))



  /* ================================================================== *
   *                   BASICBLOCK -> PREDICATE INSTRUCTION              *
   * ================================================================== */

  bb_0.io.predicateIn(0) <> InputSplitter.io.Out.enable



  /* ================================================================== *
   *                   BASICBLOCK -> PREDICATE LOOP                     *
   * ================================================================== */

  bb_1.io.predicateIn(0) <> Loop_0.io.loopExit(0)

  bb_2.io.predicateIn(1) <> Loop_0.io.activate_loop_start

  bb_2.io.predicateIn(0) <> Loop_0.io.activate_loop_back



  /* ================================================================== *
   *                   PRINTING PARALLEL CONNECTIONS                    *
   * ================================================================== */



  /* ================================================================== *
   *                   LOOP -> PREDICATE INSTRUCTION                    *
   * ================================================================== */

  Loop_0.io.enable <> br_0.io.Out(0)

  Loop_0.io.loopBack(0) <> br_13.io.FalseOutput(0)

  Loop_0.io.loopFinish(0) <> br_13.io.TrueOutput(0)



  /* ================================================================== *
   *                   ENDING INSTRUCTIONS                              *
   * ================================================================== */



  /* ================================================================== *
   *                   LOOP INPUT DATA DEPENDENCIES                     *
   * ================================================================== */

  Loop_0.io.InLiveIn(0) <> InputSplitter.io.Out.data.elements("field0")(1)



  /* ================================================================== *
   *                   LOOP DATA LIVE-IN DEPENDENCIES                   *
   * ================================================================== */

  Gep_5.io.baseAddress <> Loop_0.io.OutLiveIn.elements("field0")(0)

  Gep_9.io.baseAddress <> Loop_0.io.OutLiveIn.elements("field0")(1)



  /* ================================================================== *
   *                   LOOP DATA LIVE-OUT DEPENDENCIES                  *
   * ================================================================== */



  /* ================================================================== *
   *                   LOOP LIVE OUT DEPENDENCIES                       *
   * ================================================================== */



  /* ================================================================== *
   *                   LOOP CARRY DEPENDENCIES                          *
   * ================================================================== */

  Loop_0.io.CarryDepenIn(0) <> binaryOp_11.io.Out(0)



  /* ================================================================== *
   *                   LOOP DATA CARRY DEPENDENCIES                     *
   * ================================================================== */

  phi4.io.InData(1) <> Loop_0.io.CarryDepenOut.elements("field0")(0)



  /* ================================================================== *
   *                   BASICBLOCK -> ENABLE INSTRUCTION                 *
   * ================================================================== */

  br_0.io.enable <> bb_0.io.Out(0)


  const0.io.enable <> bb_1.io.Out(0)

  Gep_1.io.enable <> bb_1.io.Out(1)

  ld_2.io.enable <> bb_1.io.Out(2)

  ret_3.io.In.enable <> bb_1.io.Out(3)


  const1.io.enable <> bb_2.io.Out(0)

  const2.io.enable <> bb_2.io.Out(1)

  const3.io.enable <> bb_2.io.Out(2)

  const4.io.enable <> bb_2.io.Out(3)

  const5.io.enable <> bb_2.io.Out(4)

  phi4.io.enable <> bb_2.io.Out(5)

  Gep_5.io.enable <> bb_2.io.Out(6)

  ld_6.io.enable <> bb_2.io.Out(7)

  binaryOp_7.io.enable <> bb_2.io.Out(8)

  binaryOp_8.io.enable <> bb_2.io.Out(9)

  Gep_9.io.enable <> bb_2.io.Out(10)

  st_10.io.enable <> bb_2.io.Out(11)

  binaryOp_11.io.enable <> bb_2.io.Out(12)

  icmp_12.io.enable <> bb_2.io.Out(13)

  br_13.io.enable <> bb_2.io.Out(14)




  /* ================================================================== *
   *                   CONNECTING PHI NODES                             *
   * ================================================================== */

  phi4.io.Mask <> bb_2.io.MaskBB(0)



  /* ================================================================== *
   *                   PRINT ALLOCA OFFSET                              *
   * ================================================================== */



  /* ================================================================== *
   *                   CONNECTING MEMORY CONNECTIONS                    *
   * ================================================================== */

  MemCtrl.io.ReadIn(0) <> ld_2.io.memReq

  ld_2.io.memResp <> MemCtrl.io.ReadOut(0)

  MemCtrl.io.ReadIn(1) <> ld_6.io.memReq

  ld_6.io.memResp <> MemCtrl.io.ReadOut(1)

  MemCtrl.io.WriteIn(0) <> st_10.io.memReq

  st_10.io.memResp <> MemCtrl.io.WriteOut(0)



  /* ================================================================== *
   *                   PRINT SHARED CONNECTIONS                         *
   * ================================================================== */



  /* ================================================================== *
   *                   CONNECTING DATA DEPENDENCIES                     *
   * ================================================================== */

  Gep_1.io.idx(0) <> const0.io.Out

  phi4.io.InData(0) <> const1.io.Out

  binaryOp_7.io.RightIO <> const2.io.Out

  binaryOp_8.io.RightIO <> const3.io.Out

  binaryOp_11.io.RightIO <> const4.io.Out

  icmp_12.io.RightIO <> const5.io.Out

  ld_2.io.GepAddr <> Gep_1.io.Out(0)

  ret_3.io.In.data("field0") <> ld_2.io.Out(0)

  Gep_5.io.idx(0) <> phi4.io.Out(0)

  binaryOp_8.io.LeftIO <> phi4.io.Out(1)

  binaryOp_11.io.LeftIO <> phi4.io.Out(2)

  ld_6.io.GepAddr <> Gep_5.io.Out(0)

  binaryOp_7.io.LeftIO <> ld_6.io.Out(0)

  st_10.io.inData <> binaryOp_7.io.Out(0)

  Gep_9.io.idx(0) <> binaryOp_8.io.Out(0)

  st_10.io.GepAddr <> Gep_9.io.Out(0)

  icmp_12.io.LeftIO <> binaryOp_11.io.Out(1)

  br_13.io.CmpIO <> icmp_12.io.Out(0)

  Gep_1.io.baseAddress <> InputSplitter.io.Out.data.elements("field0")(0)

  st_10.io.Out(0).ready := true.B



  /* ================================================================== *
   *                   PRINTING OUTPUT INTERFACE                        *
   * ================================================================== */

  io.out <> ret_3.io.Out

}

import java.io.{File, FileWriter}

object test05Top extends App {
  val dir = new File("RTL/test05Top");
  dir.mkdirs
  implicit val p = config.Parameters.root((new MiniConfig).toInstance)
  val chirrtl = firrtl.Parser.parse(chisel3.Driver.emit(() => new test05DF()))

  val verilogFile = new File(dir, s"${chirrtl.main}.v")
  val verilogWriter = new FileWriter(verilogFile)
  val compileResult = (new firrtl.VerilogCompiler).compileAndEmit(firrtl.CircuitState(chirrtl, firrtl.ChirrtlForm))
  val compiledStuff = compileResult.getEmittedCircuit
  verilogWriter.write(compiledStuff.value)
  verilogWriter.close()
}
