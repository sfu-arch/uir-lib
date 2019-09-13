package dandelion.node

import chisel3.{RegInit, _}
import chisel3.util._
import chisel3.util.experimental.BoringUtils
import org.scalacheck.Prop.False
import dandelion.config._
import dandelion.interfaces._
import utility.Constants._
import utility.UniformPrintfs

// Design Doc
//////////
/// DRIVER ///
/// 1. Memory response only available atleast 1 cycle after request
//  2. Need registers for pipeline handshaking e.g., _valid,
// _ready need to latch ready and valid signals.
//////////

class StoreIO(NumPredOps: Int,
              NumSuccOps: Int,
              NumOuts: Int, Debug: Boolean = false)(implicit p: Parameters)
  extends HandShakingIOPS(NumPredOps, NumSuccOps, NumOuts, Debug)(new DataBundle) {
  // Node specific IO
  // GepAddr: The calculated address comming from GEP node
  val GepAddr = Flipped(Decoupled(new DataBundle))
  // Store data.
  val inData = Flipped(Decoupled(new DataBundle))
  // Memory request
  val memReq = Decoupled(new WriteReq())
  // Memory response.
  val memResp = Input(Flipped(new WriteResp()))

  override def cloneType = new StoreIO(NumPredOps, NumSuccOps, NumOuts, Debug).asInstanceOf[this.type]
}

/**
  * @brief Store Node. Implements store operations
  * @details [long description]
  * @param NumPredOps [Number of predicate memory operations]
  */
class UnTypStore(NumPredOps: Int,
                 NumSuccOps: Int,
                 NumOuts: Int = 1,
                 Typ: UInt = MT_W, ID: Int, RouteID: Int, Debug: Boolean = false)
                (implicit p: Parameters,
                 name: sourcecode.Name,
                 file: sourcecode.File)
  extends HandShaking(NumPredOps, NumSuccOps, NumOuts, ID, Debug)(new DataBundle)(p) {

  // Set up StoreIO
  override lazy val io = IO(new StoreIO(NumPredOps, NumSuccOps, NumOuts, Debug))
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


 //------------------------------

  if (ID == 7) {
    val SinkVal = Wire (UInt(6.W))
    SinkVal:= 0.U
    val Uniq_name = "me"
    BoringUtils.addSink(SinkVal, Uniq_name)
    //printf("[***************************sinksource*******************" + SinkVal)
  }
  //----------------------------------


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
  io.memReq.bits.address := addr_R.data
  io.memReq.bits.data := data_R.data
  io.memReq.bits.Typ := Typ
  io.memReq.bits.RouteID := RouteID.U
  io.memReq.bits.taskID := data_R.taskID | addr_R.taskID | enable_R.taskID
  io.memReq.bits.mask := 15.U
  io.memReq.valid := false.B

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
            io.memReq.valid := true.B
            when(io.memReq.ready) {
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
      when(io.memResp.valid) {
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

  def isDebug(): Boolean = {
    Debug
  }

}


class DebugBufferIO(NumPredOps: Int = 0,
              NumSuccOps: Int = 0,
              NumOuts: Int = 1)(implicit p: Parameters)
  extends HandShakingIOPS(NumPredOps, NumSuccOps, NumOuts)(new DataBundle) {

  // Memory request
  val memReq = Decoupled(new WriteReq())
  // Memory response.
  val memResp = Input(Flipped(new WriteResp()))

  override def cloneType = new DebugBufferIO(NumPredOps, NumSuccOps, NumOuts).asInstanceOf[this.type]
}

/**
  * @brief Store Node. Implements store operations
  * @details [long description]
  * @param NumPredOps [Number of predicate memory operations]
  */
class DebugBufferNode(NumPredOps: Int = 0, NumSuccOps: Int = 0, NumOuts: Int = 1,
                 Typ: UInt = MT_W, ID: Int, RouteID: Int)
                (implicit p: Parameters,
                 name: sourcecode.Name,  file: sourcecode.File)
  extends HandShaking(NumPredOps, NumSuccOps, NumOuts, ID)(new DataBundle)(p) {

  // Set up StoreIO
  override lazy val io = IO(new DebugBufferIO(NumPredOps, NumSuccOps, NumOuts))
  // Printf debugging
  val node_name = name.value
  val module_name = file.value.split("/").tail.last.split("\\.").head.capitalize
  override val printfSigil = "[" + module_name + "] " + node_name + ": " + ID + " "
  val (cycleCount, _) = Counter(true.B, 32 * 1024)
  val inData = new DataBundle
  val GepAddr = new DataBundle
  //------------------------------
  val dbg_counter = Counter(1024)

  val Uniq_name_Data = "meData"
  //val Uniq_name_Adr = "meAdr"
  //---------------------------
  // -------

  for (i <- 0 until NumOuts) {
    io.Out(i).bits := DataBundle.default
    io.Out(i).valid := false.B
  }

  val LogData = Module(new Queue(UInt(4.W), 4))
  val LogAddress = dbg_counter.value << 2.U

//  val LogAddress = Queue(Decoupled(UInt(10.W)),20)
  val st_node = Module(new UnTypStore(NumPredOps, NumSuccOps, ID = 0, RouteID = 0))

  //?
  st_node.io.enable.bits := ControlBundle.active()
  st_node.io.enable.valid := true.B

  //  val log_data_ready_wire = WireInit(LogData.ready)
//  val log_data_ready_wire = WireInit(LogData.ready)
//  val log_data_valid_wire = WireInit(LogData.valid)

//
//  val log_address_ready_wire = WireInit(LogAddress.ready)
//  val log_address_valid_wire = WireInit(LogAddress.valid)
//  val log_address_bits_wire = WireInit(LogAddress.bits)


  LogData.io.enq.bits := 0.U
  LogData.io.enq.valid := false.B
  LogData.io.deq.ready := true.B

  BoringUtils.addSink(LogData.io.deq.bits, "Test_data")
  BoringUtils.addSink(LogData.io.deq.valid, "Test_valid")
  BoringUtils.addSource(LogData.io.enq.ready, "Test_ready")

  io.memReq <> st_node.io.memReq
  st_node.io.memResp <> io.memResp
  //st_node.io.enable := io.enable
  st_node.io.enable.bits := ControlBundle.active()
  st_node.io.enable.valid := true.B
  st_node.io.Out(0).ready := false.B
  when(st_node.io.inData.ready && st_node.io.GepAddr.ready && LogData.io.deq.valid ){
    dbg_counter.inc()
    st_node.io.inData.enq(DataBundle(LogData.io.deq.bits))
    st_node.io.GepAddr.enq(DataBundle(LogAddress.asUInt()))
    st_node.io.Out(0).ready := true.B
  }.otherwise{
    st_node.io.inData.noenq()
    st_node.io.GepAddr.noenq()
  }



}
/*
class DebugBufferNode(NumPredOps: Int = 0, NumSuccOps: Int = 0, NumOuts: Int = 1,
                      Typ: UInt = MT_W, ID: Int, RouteID: Int)
                     (implicit p: Parameters,
                      name: sourcecode.Name,  file: sourcecode.File)
  extends HandShaking(NumPredOps, NumSuccOps, NumOuts, ID)(new DataBundle)(p) {

  // Set up StoreIO
  override lazy val io = IO(new DebugBufferIO(NumPredOps, NumSuccOps, NumOuts))
  // Printf debugging
  val node_name = name.value
  val module_name = file.value.split("/").tail.last.split("\\.").head.capitalize
  override val printfSigil = "[" + module_name + "] " + node_name + ": " + ID + " "
  val (cycleCount, _) = Counter(true.B, 32 * 1024)
  val inData = new DataBundle
  //val GepAddr = new DataBundle
  val dbg_counter = Counter(1024)

  val Uniq_name_Data = "meData"
  //val Uniq_name_Adr = "meAdr"


  val LogData = Queue(Decoupled(UInt(4.W)),20)
  //val LogAddress = Queue(Decoupled(UInt(10.W)),20)
  val st_node = Module(new UnTypStore(NumPredOps, NumSuccOps, ID = 0, RouteID = 0))

  //?
  st_node.io.enable.bits := ControlBundle.active()
  st_node.io.enable.valid := true.B


  BoringUtils.addSink(LogData, Uniq_name_Data)
  //BoringUtils.addSink(LogAddress, Uniq_name_Adr)

  val address = (dbg_counter.value << 2.U).asUInt()
  when(st_node.io.inData.ready && LogData.valid){
    //here just ++ the address
    dbg_counter.inc()
    st_node.io.inData.enq(DataBundle(LogData.deq()))
    st_node.io.GepAddr := address
  }.otherwise{
    st_node.io.inData.noenq()
    st_node.io.GepAddr.noenq()
  }

}

*/
