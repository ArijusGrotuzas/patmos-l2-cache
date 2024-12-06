/*
 * SD host controller in SPI mode.
 *
 * Authors: Max Rishoej Pedersen (MaxRishoej@gmail.com)
 *
 */

package io

import chisel3._
import chisel3.util._
import ocp._

object SDHostCtrl extends DeviceObject {
  def init(params: Map[String, String]) = {
  }

  def create(params: Map[String, String]) : SDHostCtrl = {
    Module(new SDHostCtrl())
  }
}

class SDHostCtrl() extends CoreDevice() {
  override val io = new CoreDeviceIO() with patmos.HasPins {
    val pins: Bundle {
      val sdClk: UInt
      val sdCs: UInt
      val sdDatOut: UInt
      val sdDatIn: UInt
      val sdWp: UInt
    } = new Bundle() {
      val sdClk = Output(UInt(1.W));
      val sdCs = Output(UInt(1.W));
      val sdDatOut = Input(UInt(1.W)); // Data Out on the card
      val sdDatIn = Output(UInt(1.W)); // Data In on the card
      val sdWp = Input(UInt(1.W));
    }
  }

  // Internals
  val enReg = Reg(false.B) // Is the controller enabled?
  val bufPntReg = Reg(0.U(4.W)) // Counts the bit being transmitted.
                                  // Must be large enough to contain buffer size.

  // Clock
  val DEFAULTCLKDIV = 100 // 80MHz Patmos -> 400kHz SCLK
  val clkDivReg = Reg(DEFAULTCLKDIV.U(16.W))
  val clkCntReg = Reg(0.U(16.W)) // Could be smaller
  val clkReg = Reg(true.B)

  // Buffer
  val bufOutReg = Reg(UInt(8.W))
  val bufInReg = Reg(UInt(8.W))

  // Settings register
  val csReg = Reg(false.B) // CS pin

  // OCP
  val ocpDataReg = Reg(UInt(32.W))
  val ocpRespReg = Reg(UInt(2.W))
  ocpRespReg := OcpResp.NULL

  // Write to registers
  when(io.ocp.M.Cmd === OcpCmd.WR) {
    ocpDataReg := io.ocp.M.Data
    ocpRespReg := OcpResp.DVA

    switch(io.ocp.M.Addr(5,2)) {
      // Data is written
      is("b0000".U) {
        bufOutReg := io.ocp.M.Data
        bufInReg := 0.U

        // Trigger transaction
        enReg := true.B
        bufPntReg := 8.U
        clkCntReg := clkDivReg
      }

      // Write to CS register
      is("b0001".U) {
        csReg := io.ocp.M.Data =/= 0.U
      }

      // Write to CKLDIV register
      is("b0011".U) {
        clkDivReg := io.ocp.M.Data
      }
    }
  }

  // Read from registers
  when(io.ocp.M.Cmd === OcpCmd.RD) {
    ocpRespReg := OcpResp.DVA

    switch(io.ocp.M.Addr(5,2)) {
      // Reading data. Doesn't trigger enable.
      is("b0000".U) {
        ocpDataReg := bufInReg
      }

      // Allow reading enReg
      is("b0010".U) {
        ocpDataReg := enReg
      }
    }
  }

  // Connections to master
  io.ocp.S.Resp := ocpRespReg
  io.ocp.S.Data := ocpDataReg

  // Connections to pins
  val bufIdx = bufPntReg - 1.U // For convenience

  when(enReg === true.B) {
    when (clkCntReg === 1.U) {
      clkCntReg := clkDivReg
      clkReg := ~clkReg

      when(clkReg === true.B) { // Falling edge -> Sample
        bufInReg(bufIdx) := io.pins.sdDatOut

        // Count clock cycles
        when (bufPntReg === 1.U) {
          enReg := false.B // Transaction done
        }
        .otherwise {
          bufPntReg := bufPntReg - 1.U
        }
      }
    }
    .otherwise {
      clkCntReg := clkCntReg - 1.U
    }

    io.pins.sdClk := clkReg
  }
  .otherwise { // Not enabled
    io.pins.sdClk := false.B
    clkReg := false.B // As to begin with a rising edge
  }

  io.pins.sdDatIn := bufOutReg(bufIdx)

  // Always
  io.pins.sdCs := csReg
} 
