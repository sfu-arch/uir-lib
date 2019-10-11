package dnnnode

import chisel3._
import chisel3.util._
import config._
import dnn.memory.TensorParams
import interfaces._
import node.{HandShaking, HandShakingIOPS, Shapes}
import utility.Constants._


class TStoreIO[gen <: Shapes](NumPredOps: Int, NumSuccOps: Int, NumOuts: Int)(shape: => gen)(implicit p: Parameters)
  extends HandShakingIOPS(NumPredOps, NumSuccOps, NumOuts)(new CustomDataBundle(UInt(shape.getWidth.W))) {
  val GepAddr = Flipped(Decoupled(new DataBundle))
  val inData  = Flipped(Decoupled(new TypBundle))
  val tensorReq   = Decoupled(new TensorWriteReq(shape.getWidth))
  val tensorResp  = Input(Flipped(new TensorWriteResp))

  override def cloneType = new TStoreIO(NumPredOps, NumSuccOps, NumOuts)(shape).asInstanceOf[this.type]
}

/**
 * @brief TYPE Store Node. Implements store operations
 * @details [long description]
 *
 * @param NumPredOps [Number of predicate memory operations]
 */
class TStore[L <: Shapes](NumPredOps: Int,
                          NumSuccOps: Int,
                          NumOuts: Int,
                          ID: Int = 0,
                          RouteID: Int)(shape: => L)
                         (implicit p: Parameters, name: sourcecode.Name, file: sourcecode.File)
  extends HandShaking(NumPredOps, NumSuccOps, NumOuts, ID)(new CustomDataBundle(UInt(shape.getWidth.W)))(p) {
  override lazy val io = IO(new TStoreIO(NumPredOps, NumSuccOps, NumOuts)(shape))
  // Printf debugging
  val node_name = name.value
  val module_name = file.value.split("/").tail.last.split("\\.").head.capitalize
  override val printfSigil = "[" + module_name + "] " + node_name + ": " + ID + " "
  val (cycleCount, _) = Counter(true.B, 32 * 1024)

  /*=============================================
  =            Register declarations            =
  =============================================*/

  // OP Inputs
  val addr_R = RegInit(DataBundle.default)
  val data_R = RegInit(DataBundle.default)
  val addr_valid_R = RegInit(false.B)
  val data_valid_R = RegInit(false.B)

  // State machine
  val s_idle :: s_RECEIVING :: s_Done :: Nil = Enum(3)
  val state = RegInit(s_idle)

  val ReqValid = RegInit(false.B)

  /*============================================
  =            Predicate Evaluation            =
  ============================================*/

  //  val predicate = IsEnable()
  //  val start = addr_valid_R & data_valid_R & IsPredValid() & IsEnableValid()

  /*================================================
  =            Latch inputs. Set output            =
  ================================================*/

  //Initialization READY-VALIDs for GepAddr and Predecessor memory ops
  io.GepAddr.ready := ~addr_valid_R
  io.inData.ready := ~data_valid_R

  // ACTION: GepAddr
  io.GepAddr.ready := ~addr_valid_R
  when(io.GepAddr.fire()) {
    addr_R := io.GepAddr.bits
    addr_valid_R := true.B
  }
  when(io.enable.fire()) {
    succ_bundle_R.foreach(_ := io.enable.bits)
  }
  // ACTION: inData
  when(io.inData.fire()) {
    // Latch the data
    data_R := io.inData.bits
    data_valid_R := true.B
  }

  // Wire up Outputs
  for (i <- 0 until NumOuts) {
    io.Out(i).bits := data_R
    io.Out(i).bits.taskID := data_R.taskID | addr_R.taskID | enable_R.taskID
  }
  // Outgoing Address Req ->
  io.tensorReq.bits.index := addr_R.data
  io.tensorReq.bits.data := data_R.data
//  io.tensorReq.bits.Typ := Typ
  io.tensorReq.bits.RouteID := RouteID.U
  io.tensorReq.bits.taskID := data_R.taskID | addr_R.taskID | enable_R.taskID
  io.tensorReq.bits.mask := 15.U
  io.tensorReq.valid := false.B

  /*=============================================
  =            ACTIONS (possibly dangerous)     =
  =============================================*/
  val mem_req_fire = addr_valid_R & IsPredValid() & data_valid_R
  val complete = IsSuccReady() & IsOutReady()

  switch(state) {
    is(s_idle) {
      when(enable_valid_R) {
        when(data_valid_R && addr_valid_R) {
          when(enable_R.control && mem_req_fire) {
            io.tensorReq.valid := true.B
            when(io.tensorReq.ready) {
              state := s_RECEIVING
            }
          }.otherwise {
            ValidSucc()
            ValidOut()
            data_R.predicate := false.B
            state := s_Done
          }
        }
      }
    }
    is(s_RECEIVING) {
      when(io.tensorResp.valid) {
        ValidSucc()
        ValidOut()
        state := s_Done
      }
    }
    is(s_Done) {
      when(complete) {
        // Clear all the valid states.
        // Reset address
        addr_R := DataBundle.default
        addr_valid_R := false.B
        // Reset data.
        data_R := DataBundle.default
        data_valid_R := false.B
        // Clear all other state
        Reset()
        // Reset state.
        state := s_idle
        if (log) {
          printf("[LOG] " + "[" + module_name + "] [TID->%d] [STORE]" + node_name + ": Fired @ %d Mem[%d] = %d\n",
            enable_R.taskID, cycleCount, addr_R.data, data_R.data)
          //printf("DEBUG " + node_name + ": $%d = %d\n", addr_R.data, data_R.data)
        }
      }
    }
  }
  // Trace detail.
  if (log == true && (comp contains "STORE")) {
    val x = RegInit(0.U(xlen.W))
    x := x + 1.U
    verb match {
      case "high" => {}
      case "med" => {}
      case "low" => {
        printfInfo("Cycle %d : { \"Inputs\": {\"GepAddr\": %x},", x, (addr_valid_R))
        printf("\"State\": {\"State\": \"%x\", \"data_R(Valid,Data,Pred)\": \"%x,%x,%x\" },", state, data_valid_R, data_R.data, io.Out(0).bits.predicate)
        printf("\"Outputs\": {\"Out\": %x}", io.Out(0).fire())
        printf("}")
      }
      case everythingElse => {}
    }
  }
}