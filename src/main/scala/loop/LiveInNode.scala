package node

import chisel3._
import chisel3.util._

import config._
import interfaces._
import util._

class LiveInNodeIO(NumOuts: Int)
                  (implicit p: Parameters)
  extends HandShakingIONPS(NumOuts)(new DataBundle) {

  //Input data for live in
  val InData = Flipped(Decoupled(new DataBundle()))

}

class LiveInNode(NumOuts: Int, ID: Int)
                (implicit p: Parameters, name: sourcecode.Name)
  extends HandShakingNPS(NumOuts, ID)(new DataBundle())(p) {
  override lazy val io = IO(new LiveInNodeIO(NumOuts))

  var NodeName = name.value

  // Printf debugging
  override val printfSigil = NodeName + ID + " "
  val (cycleCount,_) = Counter(true.B,32*1024)

  /*===========================================*
   *            Registers                      *
   *===========================================*/
  // In data Input
  val indata_R = RegInit(DataBundle.default)
  val indata_valid_R = RegInit(false.B)

  val s_IDLE :: s_LATCH :: Nil = Enum(2)

  val state = RegInit(s_IDLE)

  /*===============================================*
   *            LATCHING INPUTS                    *
   *===============================================*/

  io.InData.ready := ~indata_valid_R
  when(io.InData.fire()) {
    indata_R <> io.InData.bits
    indata_valid_R := true.B
  }

  /*===============================================*
   *            DEFINING STATES                    *
   *===============================================*/

  switch(state){
    is(s_IDLE){
      when(io.InData.fire()){
        state := s_LATCH
        ValidOut()
        printf("[LOG] " + NodeName + ": Latch fired @ %d, Value:%d\n",cycleCount, io.InData.bits.data.asUInt())
      }
    }
    is(s_LATCH){
      when(IsOutReady()){
        out_ready_R := VecInit(Seq.fill(NumOuts)(false.B))
        ValidOut()
      }
      when(enable_R && enable_valid_R){
        printf("[LOG] " + NodeName + ": Latch reset @ %d\n",cycleCount)
        state := s_IDLE
        indata_R <> DataBundle.default
        indata_valid_R := false.B
        Reset()
      }
    }
  }

  /*===============================================*
   *            CONNECTING OUTPUTS                 *
   *===============================================*/

  for (i <- 0 until NumOuts) {
    io.Out(i).bits <> indata_R
  }
}
