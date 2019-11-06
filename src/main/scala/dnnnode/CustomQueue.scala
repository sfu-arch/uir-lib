package dnnnode

import Chisel.Enum
import chisel3.experimental.{DataMirror, requireIsChiselType}
import chisel3.util._
import chisel3.{Flipped, Module, UInt, _}
import config.{Parameters, XLEN}

class CustomQueueIO[T <: Data](private val gen: T, val entries: Int, NumOuts: Int) extends Bundle
{
  /** I/O to enqueue data (client is producer, and Queue object is consumer), is [[Chisel.DecoupledIO]] flipped. */
  val enq = Flipped(EnqIO(gen))
  /** I/O to dequeue data (client is consumer and Queue object is producer), is [[Chisel.DecoupledIO]]*/
  val deq = Flipped(DeqIO(gen))
  val Out = ValidIO(Vec(NumOuts, gen))
  /** The current amount of data in the queue */
  val count = Output(UInt(log2Ceil(entries + 1).W))
}

/** A hardware module implementing a Queue
  * @param gen The type of data to queue
  * @param entries The max number of entries in the queue
  * @param pipe True if a single entry queue can run at full throughput (like a pipeline). The ''ready'' signals are
  * combinationally coupled.
  * @param flow True if the inputs can be consumed on the same cycle (the inputs "flow" through the queue immediately).
  * The ''valid'' signals are coupled.
  *
  * @example {{{
  * val q = Module(new Queue(UInt(), 16))
  * q.io.enq <> producer.io.out
  * consumer.io.in <> q.io.deq
  * }}}
  */
class CustomQueue[T <: Data](gen: T,
                       val entries: Int, NumOuts: Int,
                       pipe: Boolean = false,
                       flow: Boolean = false)
                      (implicit compileOptions: chisel3.CompileOptions , p: Parameters)
  extends Module() {
  require(entries > -1, "Queue must have non-negative number of entries")
  require(entries != 0, "Use companion object Queue.apply for zero entries")
  val genType = if (compileOptions.declaredTypeMustBeUnbound) {
    requireIsChiselType(gen)
    gen
  } else {
    if (DataMirror.internal.isSynthesizable(gen)) {
      chiselTypeOf(gen)
    } else {
      gen
    }
  }

  val io = IO(new CustomQueueIO(genType, entries, NumOuts))

  val ram = Mem(entries, genType)
  val enq_ptr = Counter(entries)
  val deq_ptr = Counter(entries)
  val maybe_full = RegInit(false.B)

  val ptr_match = enq_ptr.value === deq_ptr.value
  val empty = ptr_match && !maybe_full
  val full = ptr_match && maybe_full
  val do_enq = WireDefault(io.enq.fire())
  val do_deq = WireDefault(io.deq.fire())

  when (do_enq) {
    ram(enq_ptr.value) := io.enq.bits
    enq_ptr.inc()
  }
  when (do_deq) {
    deq_ptr.inc()
  }
  when (do_enq =/= do_deq) {
    maybe_full := do_enq
  }

  io.deq.valid := !empty
  io.enq.ready := !full
  io.deq.bits := ram(deq_ptr.value)

  val ptr_diff = enq_ptr.value - deq_ptr.value
  val bufCount = io.count

  val bufferOut = Wire(Vec(NumOuts, gen))
  val transposed = Wire(Vec(NumOuts, Vec(NumOuts, UInt(p(XLEN).W))))

  when (bufCount > (NumOuts - 1).U) {
    io.Out.valid := true.B
  }.otherwise {
    io.Out.valid := false.B
  }
  for (i <- 0 until NumOuts) {
    bufferOut(i) := Mux(bufCount > (NumOuts - 1).U, ram(deq_ptr.value + i.U), 0.U.asTypeOf(genType))
  }

  /*===========================================*
   *    Transposing the buffer's output        *
   *===========================================*/
  for (i <- 0 until NumOuts) {
    for (j <- 0 until NumOuts) {
      val index = ((i + 1) * p(XLEN)) - 1
      transposed(i)(j) := bufferOut(j).asUInt()(index, i * p(XLEN))
    }
  }

  for (i <- 0 until NumOuts) {
    io.Out.bits(i) := transposed(i).asTypeOf(genType)
  }


  if (flow) {
    when (io.enq.valid) { io.deq.valid := true.B }
    when (empty) {
      io.deq.bits := io.enq.bits
      do_deq := false.B
      when (io.deq.ready) { do_enq := false.B }
    }
  }

  if (pipe) {
    when (io.deq.ready) { io.enq.ready := true.B }
  }

  if (isPow2(entries)) {
    io.count := Mux(maybe_full && ptr_match, entries.U, 0.U) | ptr_diff
  } else {
    io.count := Mux(ptr_match,
      Mux(maybe_full,
        entries.asUInt, 0.U),
      Mux(deq_ptr.value > enq_ptr.value,
        entries.asUInt + ptr_diff, ptr_diff))
  }
}


