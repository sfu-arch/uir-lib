package dataflow

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
import junctions._


/**
  * This Object should be initialized at the first step
  * It contains all the transformation from indices to their module's name
  */

object Data_stencil_FlowParam {

  val bb_entry_pred = Map(
    "active" -> 0
  )


  val bb_pfor_cond_pred = Map(
    "br0" -> 0,
    "br6" -> 1
  )


  val bb_pfor_detach_pred = Map(
    "br3" -> 0
  )


  val bb_pfor_end_pred = Map(
    "br3" -> 0
  )


  val br0_brn_bb = Map(
    "bb_pfor_cond" -> 0
  )


  val br3_brn_bb = Map(
    "bb_pfor_detach" -> 0,
    "bb_pfor_end" -> 1
  )


  val br6_brn_bb = Map(
    "bb_pfor_cond" -> 0
  )


  val bb_pfor_inc_pred = Map(
    "detach4" -> 0
  )


  val bb_offload_pfor_body_pred = Map(
    "detach4" -> 0
  )


  val detach4_brn_bb = Map(
    "bb_offload_pfor_body" -> 0,
    "bb_pfor_inc" -> 1
  )


  val bb_entry_activate = Map(
    "br0" -> 0
  )


  val bb_pfor_cond_activate = Map(
    "phi1" -> 0,
    "icmp2" -> 1,
    "br3" -> 2
  )


  val bb_pfor_detach_activate = Map(
    "detach4" -> 0
  )


  val bb_pfor_inc_activate = Map(
    "add5" -> 0,
    "br6" -> 1
  )


  val bb_pfor_end_activate = Map(
    "sync7" -> 0
  )


  val bb_pfor_end_continue_activate = Map(
    "ret8" -> 0
  )


  val bb_offload_pfor_body_activate = Map(
    "call9" -> 0,
    "reattach10" -> 1
  )


  val phi1_phi_in = Map(
    "const_0" -> 0,
    "add5" -> 1
  )


  //  %pos.0 = phi i32 [ 0, %entry ], [ %inc29, %pfor.inc ], !UID !10, !ScalaLabel !11
  val phi1_in = Map(
    "add5" -> 0
  )


  //  %cmp = icmp slt i32 %pos.0, 16, !UID !12, !ScalaLabel !13
  val icmp2_in = Map(
    "phi1" -> 0
  )


  //  br i1 %cmp, label %pfor.detach, label %pfor.end, !UID !14, !BB_UID !15, !ScalaLabel !16
  val br3_in = Map(
    "icmp2" -> 0
  )


  //  detach label %offload.pfor.body, label %pfor.inc, !UID !17, !BB_UID !18, !ScalaLabel !19
  val detach4_in = Map(
    "" -> 0,
    "" -> 1
  )


  //  %inc29 = add nsw i32 %pos.0, 1, !UID !20, !ScalaLabel !21
  val add5_in = Map(
    "phi1" -> 1
  )


  //  sync label %pfor.end.continue, !UID !35, !BB_UID !36, !ScalaLabel !37
  val sync7_in = Map(
    "" -> 2
  )


  //  ret void, !UID !38, !BB_UID !39, !ScalaLabel !40
  val ret8_in = Map(

  )


  //  call void @stencil_detach1(i32 %pos.0, i32* %in, i32* %out), !UID !41, !ScalaLabel !42
  val call9_in = Map(
    "phi1" -> 2,
    "field0" -> 0,
    "field1" -> 0,
    "" -> 3
  )


  //  reattach label %pfor.inc, !UID !43, !BB_UID !44, !ScalaLabel !45
  val reattach10_in = Map(
    "" -> 4
  )


}


/* ================================================================== *
 *                   PRINTING PORTS DEFINITION                        *
 * ================================================================== */


abstract class stencilDFIO(implicit val p: Parameters) extends Module with CoreParams {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new Call(List(32, 32))))
    val call9_out = Decoupled(new Call(List(32, 32, 32)))
    val call9_in = Flipped(Decoupled(new Call(List(32))))
    //    val CacheResp = Flipped(Valid(new CacheRespT))
    //    val CacheReq = Decoupled(new CacheReq)
    val out = Decoupled(new Call(List(32)))
  })
}


/* ================================================================== *
 *                   PRINTING MODULE DEFINITION                       *
 * ================================================================== */


class stencilDF(implicit p: Parameters) extends stencilDFIO()(p) {


  /* ================================================================== *
   *                   PRINTING MEMORY SYSTEM                           *
   * ================================================================== */

  /*
    val StackPointer = Module(new Stack(NumOps = 1))

    val RegisterFile = Module(new TypeStackFile(ID=0,Size=32,NReads=2,NWrites=2)
                  (WControl=new WriteMemoryController(NumOps=2,BaseSize=2,NumEntries=2))
                  (RControl=new ReadMemoryController(NumOps=2,BaseSize=2,NumEntries=2)))

    val CacheMem = Module(new UnifiedController(ID=0,Size=32,NReads=2,NWrites=2)
                  (WControl=new WriteMemoryController(NumOps=2,BaseSize=2,NumEntries=2))
                  (RControl=new ReadMemoryController(NumOps=2,BaseSize=2,NumEntries=2))
                  (RWArbiter=new ReadWriteArbiter()))

    io.CacheReq <> CacheMem.io.CacheReq
    CacheMem.io.CacheResp <> io.CacheResp
  */
  val InputSplitter = Module(new SplitCall(List(32, 32)))
  InputSplitter.io.In <> io.in


  /* ================================================================== *
   *                   PRINTING LOOP HEADERS                            *
   * ================================================================== */


  val loop_L_0_liveIN_0 = Module(new LiveInNewNode(NumOuts = 1, ID = 0))
  val loop_L_0_liveIN_1 = Module(new LiveInNewNode(NumOuts = 1, ID = 0))

  val loop_L_0_liveIN_DELAY_0 = Module(new LiveOutNode(NumOuts = 1, ID = 0))
  val loop_L_0_liveIN_DELAY_1 = Module(new LiveOutNode(NumOuts = 1, ID = 0))

  val phi_L_DELAY_0 = Module(new LiveOutNode(NumOuts = 1, ID = 0))
  val phi_L_DELAY_1 = Module(new LiveOutNode(NumOuts = 1, ID = 0))

  /* ================================================================== *
   *                   PRINTING BASICBLOCK NODES                        *
   * ================================================================== */


  //Initializing BasicBlocks: 

  val bb_entry = Module(new BasicBlockNoMaskNode(NumInputs = 1, NumOuts = 3, BID = 0))

  val bb_pfor_cond = Module(new LoopHead(NumOuts = 3, NumPhi = 1, BID = 1))

  //  val bb_pfor_detach = Module(new BasicBlockNoMaskNode(NumInputs = 1, NumOuts = 1, BID = 2))

  val bb_pfor_inc = Module(new BasicBlockNoMaskNode(NumInputs = 2, NumOuts = 2, BID = 3))

  //  val bb_pfor_end = Module(new BasicBlockNoMaskNode(NumInputs = 1, NumOuts = 3, BID = 4))
  val tmp_bb_my_pfor_end = Module(new UBranchNode(NumOuts = 2, ID = 22))

  val bb_pfor_end_continue = Module(new BasicBlockNoMaskNode(NumInputs = 1, NumOuts = 1, BID = 5))

  val bb_offload_pfor_body = Module(new BasicBlockNoMaskNode(NumInputs = 1, NumOuts = 1, BID = 6))


  /* ================================================================== *
   *                   PRINTING INSTRUCTION NODES                       *
   * ================================================================== */


  //Initializing Instructions: 

  // [BasicBlock]  entry:

  //  br label %pfor.cond, !UID !7, !BB_UID !8, !ScalaLabel !9
  val br0 = Module(new UBranchFastNode(ID = 0))

  // [BasicBlock]  pfor.cond:

  //  %pos.0 = phi i32 [ 0, %entry ], [ %inc29, %pfor.inc ], !UID !10, !ScalaLabel !11
  val phi1 = Module(new PhiNode(NumInputs = 2, NumOuts = 3, ID = 1))


  //  %cmp = icmp slt i32 %pos.0, 16, !UID !12, !ScalaLabel !13
  val icmp2 = Module(new IcmpNode(NumOuts = 1, ID = 2, opCode = "ULT")(sign = false))


  //  br i1 %cmp, label %pfor.detach, label %pfor.end, !UID !14, !BB_UID !15, !ScalaLabel !16
  val br3 = Module(new CBranchNode(ID = 3))

  // [BasicBlock]  pfor.detach:

  //  detach label %offload.pfor.body, label %pfor.inc, !UID !17, !BB_UID !18, !ScalaLabel !19
  val detach4 = Module(new DetachNode(NumOuts = 7, ID = 4))

  // [BasicBlock]  pfor.inc:

  //  %inc29 = add nsw i32 %pos.0, 1, !UID !20, !ScalaLabel !21
  val add5 = Module(new ComputeNode(NumOuts = 1, ID = 5, opCode = "add")(sign = false))


  //  br label %pfor.cond, !llvm.loop !22, !UID !32, !BB_UID !33, !ScalaLabel !34
  val br6 = Module(new UBranchFastNode(ID = 6))

  // [BasicBlock]  pfor.end:

  //  sync label %pfor.end.continue, !UID !35, !BB_UID !36, !ScalaLabel !37
  val sync7 = Module(new SyncNode(ID = 7, NumOuts = 1))

  // [BasicBlock]  pfor.end.continue:

  //  ret void, !UID !38, !BB_UID !39, !ScalaLabel !40
  val ret8 = Module(new RetNode(NumPredIn = 1, retTypes = List(32), ID = 8))

  // [BasicBlock]  offload.pfor.body:

  //  call void @stencil_detach1(i32 %pos.0, i32* %in, i32* %out), !UID !41, !ScalaLabel !42
  val call9 = Module(new CallNode(ID = 9, argTypes = List(32, 32, 32), retTypes = List(32)))


  //  reattach label %pfor.inc, !UID !43, !BB_UID !44, !ScalaLabel !45
  val reattach10 = Module(new ReattachNode(ID = 10))


  /* ================================================================== *
   *                   INITIALIZING PARAM                               *
   * ================================================================== */


  /**
    * Instantiating parameters
    */
  val param = Data_stencil_FlowParam


  /* ================================================================== *
   *                   CONNECTING BASIC BLOCKS TO PREDICATE INSTRUCTIONS*
   * ================================================================== */


  /**
    * Connecting basic blocks to predicate instructions
    */


  bb_entry.io.predicateIn <> InputSplitter.io.Out.enable

  /**
    * Connecting basic blocks to predicate instructions
    */

  //Connecting br0 to bb_pfor_cond
  bb_pfor_cond.io.activate <> br0.io.Out(0) // Manual change


  //Connecting br3 to bb_pfor_detach
  //  bb_pfor_detach.io.predicateIn <> br3.io.Out(param.br3_brn_bb("bb_pfor_detach"))
  tmp_bb_my_pfor_end.io.enable <> br3.io.Out(1)


  //Connecting br3 to bb_pfor_end
  //  bb_pfor_end.io.predicateIn <> br3.io.Out(param.br3_brn_bb("bb_pfor_end"))
  detach4.io.enable <> br3.io.Out(0)


  //Connecting br6 to bb_pfor_cond
  bb_pfor_cond.io.loopBack <> br6.io.Out(0) // Manual change


  //Connecting detach4 to bb_offload_pfor_body
  bb_offload_pfor_body.io.predicateIn <> detach4.io.Out(0)


  //Connecting detach4 to bb_pfor_inc
  bb_pfor_inc.io.predicateIn <> detach4.io.Out(1)

  bb_pfor_end_continue.io.predicateIn <> sync7.io.Out(0)


  /* ================================================================== *
   *                   CONNECTING BASIC BLOCKS TO INSTRUCTIONS          *
   * ================================================================== */


  /**
    * Wiring enable signals to the instructions
    */

  br0.io.enable <> bb_entry.io.Out(0)


  phi1.io.enable <> bb_pfor_cond.io.Out(param.bb_pfor_cond_activate("phi1"))

  icmp2.io.enable <> bb_pfor_cond.io.Out(param.bb_pfor_cond_activate("icmp2"))

  br3.io.enable <> bb_pfor_cond.io.Out(param.bb_pfor_cond_activate("br3"))


  //  detach4.io.enable <> bb_pfor_detach.io.Out(param.bb_pfor_detach_activate("detach4"))


  add5.io.enable <> bb_pfor_inc.io.Out(param.bb_pfor_inc_activate("add5"))

  br6.io.enable <> bb_pfor_inc.io.Out(param.bb_pfor_inc_activate("br6"))


  loop_L_0_liveIN_0.io.enable <> bb_entry.io.Out(1)
  loop_L_0_liveIN_1.io.enable <> bb_entry.io.Out(2)

  loop_L_0_liveIN_0.io.Invalid <> tmp_bb_my_pfor_end.io.Out(0)
  loop_L_0_liveIN_1.io.Invalid <> tmp_bb_my_pfor_end.io.Out(1)

  loop_L_0_liveIN_DELAY_0.io.enable <> detach4.io.Out(3)
  loop_L_0_liveIN_DELAY_1.io.enable <> detach4.io.Out(4)

  phi_L_DELAY_0.io.enable <> detach4.io.Out(5)
  phi_L_DELAY_1.io.enable <> detach4.io.Out(6)

  ret8.io.enable <> bb_pfor_end_continue.io.Out(param.bb_pfor_end_continue_activate("ret8"))


  call9.io.In.enable <> bb_offload_pfor_body.io.Out(param.bb_offload_pfor_body_activate("call9"))

  // Manual fix.  Reattach should only depend on its increment/decr inputs. If it is connected to the BB
  // Enable it will stall the loop
  //  reattach10.io.enable <> bb_offload_pfor_body.io.Out(param.bb_offload_pfor_body_activate("reattach10"))
  //  reattach10.io.enable.enq(ControlBundle.active()) // always enabled
  //  bb_offload_pfor_body.io.Out(param.bb_offload_pfor_body_activate("reattach10")).ready := true.B


  /* ================================================================== *
   *                   CONNECTING LOOPHEADERS                           *
   * ================================================================== */


  // Connecting function argument to the loop header
  //i32* %in
  loop_L_0_liveIN_0.io.InData <> InputSplitter.io.Out.data("field0")
  loop_L_0_liveIN_DELAY_0.io.InData <> loop_L_0_liveIN_0.io.Out(0)

  // Connecting function argument to the loop header
  //i32* %out
  loop_L_0_liveIN_1.io.InData <> InputSplitter.io.Out.data("field1")
  loop_L_0_liveIN_DELAY_1.io.InData <> loop_L_0_liveIN_1.io.Out(0)


  /* ================================================================== *
   *                   DUMPING PHI NODES                                *
   * ================================================================== */


  /**
    * Connecting PHI Masks
    */
  //Connect PHI node

  phi1.io.InData(param.phi1_phi_in("const_0")).bits.data := 0.U
  phi1.io.InData(param.phi1_phi_in("const_0")).bits.predicate := true.B
  phi1.io.InData(param.phi1_phi_in("const_0")).valid := true.B

  phi1.io.InData(param.phi1_phi_in("add5")) <> add5.io.Out(param.phi1_in("add5"))

  /**
    * Connecting PHI Masks
    */
  //Connect PHI node

  phi1.io.Mask <> bb_pfor_cond.io.MaskBB(0)


  /* ================================================================== *
   *                   DUMPING DATAFLOW                                 *
   * ================================================================== */


  /**
    * Connecting Dataflow signals
    */

  // Wiring instructions
  icmp2.io.LeftIO <> phi1.io.Out(param.icmp2_in("phi1"))

  // Wiring constant
  icmp2.io.RightIO.bits.data := 16.U
  icmp2.io.RightIO.bits.predicate := true.B
  icmp2.io.RightIO.valid := true.B

  // Wiring Branch instruction
  br3.io.CmpIO <> icmp2.io.Out(param.br3_in("icmp2"))

  // Wiring instructions
  //  add5.io.LeftIO <> phi1.io.Out(param.add5_in("phi1"))
  phi_L_DELAY_0.io.InData <> phi1.io.Out(1)
  add5.io.LeftIO <> phi_L_DELAY_0.io.Out(0)

  // Wiring constant
  add5.io.RightIO.bits.data := 1.U
  add5.io.RightIO.bits.predicate := true.B
  add5.io.RightIO.valid := true.B

  // Reattach (Manual add)
  reattach10.io.dataIn <> call9.io.Out.data("field0")

  // Sync (Manual add)
  sync7.io.incIn <> detach4.io.Out(2)
  sync7.io.decIn <> reattach10.io.Out(0)

  /**
    * Connecting Dataflow signals
    */
  ret8.io.predicateIn(0).bits.control := true.B
  ret8.io.predicateIn(0).bits.taskID := 0.U
  ret8.io.predicateIn(0).valid := true.B
  ret8.io.In.data("field0").bits.data := 1.U
  ret8.io.In.data("field0").bits.predicate := true.B
  ret8.io.In.data("field0").valid := true.B
  io.out <> ret8.io.Out


  // Wiring Call to I/O
  io.call9_out <> call9.io.callOut
  call9.io.retIn <> io.call9_in
  call9.io.Out.enable.ready := true.B // Manual fix

  // Wiring instructions
  //  call9.io.In.data("field0") <> phi1.io.Out(param.call9_in("phi1"))
  phi_L_DELAY_1.io.InData <> phi1.io.Out(2)
  call9.io.In.data("field0") <> phi_L_DELAY_1.io.Out(0)

  // Wiring Call to the function argument
  call9.io.In.data("field1") <> loop_L_0_liveIN_DELAY_0.io.Out(param.call9_in("field0")) // Manually added

  // Wiring Call to the function argument
  call9.io.In.data("field2") <> loop_L_0_liveIN_DELAY_1.io.Out(param.call9_in("field1")) // Manually added


}

import java.io.{File, FileWriter}

object stencilMain extends App {
  val dir = new File("RTL/stencil");
  dir.mkdirs
  implicit val p = config.Parameters.root((new MiniConfig).toInstance)
  val chirrtl = firrtl.Parser.parse(chisel3.Driver.emit(() => new stencilDF()))

  val verilogFile = new File(dir, s"${chirrtl.main}.v")
  val verilogWriter = new FileWriter(verilogFile)
  val compileResult = (new firrtl.VerilogCompiler).compileAndEmit(firrtl.CircuitState(chirrtl, firrtl.ChirrtlForm))
  val compiledStuff = compileResult.getEmittedCircuit
  verilogWriter.write(compiledStuff.value)
  verilogWriter.close()
}

