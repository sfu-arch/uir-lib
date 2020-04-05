package dandelion.node

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._
import chisel3.util.experimental.BoringUtils
import dandelion.config._
import dandelion.interfaces._
import dandelion.shell.DMEWriteMaster
import utility.Constants._
import utility.UniformPrintfs

/**
 * @brief Store Node. Implements store operations
 * @details [long description]
 */

class DebugBufferNode(
                       NumOuts: Int = 1,
                       Typ: UInt = MT_W, ID: Int, RouteID: Int, Bore_ID: Int, node_cnt: UInt)
                     (implicit val p: Parameters,
                      name: sourcecode.Name,
                      file: sourcecode.File)
  extends Module with HasAccelParams with UniformPrintfs {


  val io = IO(new Bundle {
    //  // Memory request
    val memReq = Decoupled(new WriteReq())
    //  // Memory response.
    val memResp = Input(Flipped(new WriteResp()))

    val Enable = Input(Bool())
  })
  val node_name = name.value
  val module_name = file.value.split("/").tail.last.split("\\.").head.capitalize
  override val printfSigil = "[" + module_name + "] " + node_name + ": " + ID + " "
  val (cycleCount, _) = Counter(true.B, 32 * 1024)
  val inData = new DataBundle
  val GepAddr = new DataBundle
  //------------------------------
  val dbg_counter = Counter(1024)

  //Is the Data The Wires of the Boring Connection Will put data in.
  val LogData = Module(new Queue(UInt((xlen).W), 20))


  val st_node = Module(new UnTypDebugStore(ID = 0, RouteID = RouteID))

  LogData.io.enq.bits := 0.U
  LogData.io.enq.valid := false.B
  LogData.io.deq.ready := false.B

  val queue_data = WireInit(0.U((xlen).W))
  val queue_valid = WireInit(false.B)
  val queue_ready = WireInit(false.B)

  BoringUtils.addSink(queue_data, "data" + Bore_ID)
  BoringUtils.addSink(queue_valid, "valid" + Bore_ID)
  BoringUtils.addSource(queue_ready, "ready" + Bore_ID)

  LogData.io.enq.bits := queue_data
  LogData.io.enq.valid := queue_valid && io.Enable
  queue_ready := LogData.io.enq.ready


  st_node.io.InData.bits := DataBundle(LogData.io.deq.bits)
  st_node.io.InData.valid := LogData.io.deq.valid
  LogData.io.deq.ready := st_node.io.InData.ready

  io.memReq <> st_node.io.memReq
  st_node.io.memResp <> io.memResp


  var (addr_cnt, wrap) = Counter(st_node.io.InData.fire, 4096)

  //  addr_cnt = node_cnt * 20.U

  val addr_offset = node_cnt * 20.U
  val addr = addr_offset + (addr_cnt << 2.U).asUInt()


  st_node.io.GepAddr.bits := DataBundle(addr)
  st_node.io.GepAddr.valid := true.B


}


/**
 * This is a test for debug store node
 *
 * @brief Store Node. Implements store operations
 * @details [long description]
 */
class UnTypDebugStore(
                       NumOuts: Int = 1,
                       Typ: UInt = MT_W, ID: Int, RouteID: Int)
                     (implicit val p: Parameters,
                      name: sourcecode.Name,
                      file: sourcecode.File)
  extends Module with HasAccelParams with UniformPrintfs {

  // Set up StoreIO
  val io = IO(new Bundle {

    //Input address
    val GepAddr = Flipped(Decoupled(new DataBundle()))
    //Input data
    val InData = Flipped(Decoupled(new DataBundle()))

    // Memory request
    val memReq = Decoupled(new WriteReq())
    // Memory response.
    val memResp = Flipped(Valid(new WriteResp()))
  })
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
  val addr_valid_R = RegInit(false.B)

  val data_R = RegInit(DataBundle.default)
  val data_valid_R = RegInit(false.B)

  // State machine
  val s_idle :: s_RECEIVING :: s_Done :: Nil = Enum(3)
  val state = RegInit(s_idle)


  // GepAddr
  io.GepAddr.ready := ~addr_valid_R
  when(io.GepAddr.fire()) {
    addr_R := io.GepAddr.bits
    addr_valid_R := true.B
  }

  // InData
  io.InData.ready := ~data_valid_R
  when(io.InData.fire()) {
    data_R := io.InData.bits
    data_valid_R := true.B
  }

  // Default values for memory ports
  io.memReq.bits.address := addr_R.data
  io.memReq.bits.data := data_R.data
  io.memReq.bits.Typ := Typ
  io.memReq.bits.RouteID := RouteID.U
  io.memReq.bits.taskID := data_R.taskID | addr_R.taskID
  io.memReq.bits.mask := 15.U
  io.memReq.valid := false.B

  /*=============================================
  =            ACTIONS (possibly dangerous)     =
  =============================================*/
  val latch_data = addr_valid_R && data_valid_R

  switch(state) {
    is(s_idle) {
      when(latch_data) {
        io.memReq.valid := true.B
        when(io.memReq.fire) {
          state := s_RECEIVING
        }
      }
    }
    is(s_RECEIVING) {
      when(io.memResp.valid) {
        state := s_Done
      }
    }
    is(s_Done) {
      // Clear all the valid states.
      addr_R := DataBundle.default
      addr_valid_R := false.B
      // Reset data.
      data_R := DataBundle.default
      data_valid_R := false.B

      state := s_idle
      if (log) {
        printf("[LOG] " + "[" + module_name + "] [TID->%d] [STORE]" + node_name + ": Fired @ %d Mem[%d] = %d\n",
          data_R.taskID, cycleCount, addr_R.data, data_R.data)
      }
    }
  }
}


/**
 * DebugVMEBufferNode
 * The debug buffer nodes that is connected to design's node using boreID
 * and use DME interface to flush data to off-chip memory
 *
 * @param BufferLen Length of buffer memory
 * @param ID
 * @param Bore_ID
 * @param p
 * @param name
 * @param file
 */
class DebugVMEBufferNode(BufferLen: Int = 20, ID: Int, Bore_ID: Int)
                        (implicit val p: Parameters,
                         name: sourcecode.Name,
                         file: sourcecode.File)
  extends Module with HasAccelParams with HasAccelShellParams with UniformPrintfs {


  val io = IO(new Bundle {

    val addrDebug = Input(UInt(memParams.addrBits.W))
    val vmeOut = new DMEWriteMaster
    val Enable = Input(Bool())
  })

  val node_name = name.value
  val module_name = file.value.split("/").tail.last.split("\\.").head.capitalize
  override val printfSigil = "[" + module_name + "] " + node_name + ": " + ID + " "
  val (cycleCount, _) = Counter(true.B, 32 * 1024)
  val inData = new DataBundle
  val GepAddr = new DataBundle
  //------------------------------
  val dbg_counter = Counter(1024)

  val N = RegInit(32.U)
  val sIdel :: sReq :: sBusy :: Nil = Enum(3)
  val wState = RegInit(sIdel)

  //Is the Data The Wires of the Boring Connection Will put data in.
  val LogData = Module(new Queue(UInt((xlen).W), BufferLen))

  val queue_count = RegInit(0.U(log2Ceil(BufferLen).W))
  when(LogData.io.enq.fire) {
    queue_count := queue_count + 1.U
  }

  LogData.io.enq.bits := 0.U

  val queue_data = WireInit(0.U((xlen).W))
  val queue_valid = WireInit(false.B)
  val queue_ready = WireInit(false.B)

  BoringUtils.addSink(queue_data, "data" + Bore_ID)
  BoringUtils.addSink(queue_valid, "valid" + Bore_ID)
  BoringUtils.addSource(queue_ready, "ready" + Bore_ID)

  val writeFinished = Wire(Bool())
  writeFinished := false.B
  BoringUtils.addSink(writeFinished, "RunFinished" + ID)

  LogData.io.enq.bits := queue_data
  LogData.io.enq.valid := queue_valid & io.Enable
  queue_ready := LogData.io.enq.ready

  io.vmeOut.cmd.bits.addr := io.addrDebug
  io.vmeOut.cmd.bits.len := queue_count - 1.U
  io.vmeOut.cmd.valid := (wState === sReq)


  switch(wState) {
    is(sIdel) {
      when((writeFinished && LogData.io.deq.valid) || (LogData.io.count.asUInt() === N.asUInt() / 2.U)) {
        wState := sReq
      }
    }
    is(sReq) {
      queue_ready := false.B
      when(io.vmeOut.cmd.fire()) {
        wState := sBusy
      }
    }
    is(sBusy) {
      when(io.vmeOut.ack) {
        wState := sIdel
        queue_count := 0.U
      }
    }
  }


  io.vmeOut.data.bits := 0.U
  io.vmeOut.data.valid := false.B
  LogData.io.deq.ready := false.B

  when(wState === sBusy) {
    io.vmeOut.data.bits := LogData.io.deq.bits
    io.vmeOut.data.valid := LogData.io.deq.valid
    LogData.io.deq.ready := io.vmeOut.data.ready
  }

}

