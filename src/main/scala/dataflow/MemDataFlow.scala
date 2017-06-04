package dataflow

import chisel3._
import chisel3.util._

import node._
import config._
import interfaces._
import arbiters._


abstract class MemDFIO()(implicit val p: Parameters) extends Module with CoreParams{

  val io = IO(new Bundle{
    val memOpAck = Decoupled(UInt(1.W)) //TODO 0 bits
  })
}

class MemDataFlow(implicit p: Parameters) extends MemDFIO()(p){


  //class LoadSimpleNode(NumPredMemOps: Int,
    //NumSuccMemOps: Int,
    //NumOuts: Int,
    //Typ: UInt = MT_W, ID: Int)(implicit p: Parameters)

  val m0 = Module(new StoreSimpleNode(1,1,1,ID=0)(p))
  val m1 = Module(new LoadSimpleNode(1,1,1,ID=1)(p))
  val counter = RegInit(400.U(32.W))
  counter := counter + 500.U
  val m2 = Module(new CentralizedStackRegFile(Size=32, NReads=1, NWrites=1))

  m2.io.WriteIn(0) <> m0.io.memReq
  m0.io.memResp    <> m2.io.WriteOut(0)
  
  m2.io.ReadIn(0)  <> m1.io.memReq
  m1.io.memResp    <> m2.io.ReadOut(0)
  
  m1.io.PredMemOp(0) <> m0.io.SuccMemOp(0)
  m0.io.GepAddr.bits.data := 12.U
  m0.io.inData.bits.data := counter
  m0.io.GepAddr.valid := true.B
  m0.io.inData.valid := true.B
  m0.io.PredMemOp(0).valid := true.B

  m0.io.Out(0).ready := true.B
  m1.io.GepAddr.bits.data := 12.U
  m1.io.GepAddr.valid := true.B
  
  m1.io.SuccMemOp(0).ready := true.B
  m1.io.Out(0).ready := true.B


  //m0.io.memResp.data  := m1.io.ReadOut(0).data

  //m0.io.gepAddr       <> io.gepAddr
  //m0.io.predMemOp(0)  <> io.predMemOp
  //io.memOpAck         <> m0.io.memOpAck
}
