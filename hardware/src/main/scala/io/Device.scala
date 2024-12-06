/*
 * Common definitions for I/O devices
 *
 * Authors: Wolfgang Puffitsch (wpuffitsch@gmail.com)
 *
 */

package io

import chisel3._

import ocp._

import patmos.Constants._

abstract class DeviceObject() {
  // every device object must have methods "create" and "init"
  def init(params: Map[String, String]): Unit
  def create(params: Map[String, String]) : Device

  // helper functions for parameter parsing

  def getParam(params: Map[String, String], key: String) : String = {
    val param = params.get(key)
    if (param == None) {
      throw new IllegalArgumentException("Parameter " + key + " not found")
    }
    param.get
  }

  def getIntParam(params: Map[String, String], key: String) : Int = {
    val param = getParam(params, key)
    try { param.toInt
    } catch { case exc : Exception =>
      throw new IllegalArgumentException("Parameter " + key + " must be an integer")
    }
  }

  def getPosIntParam(params: Map[String, String], key: String) : Int = {
    val param = getIntParam(params, key)
    if (param <= 0) {
      throw new IllegalArgumentException("Parameter " + key + " must be a positive integer")
    }
    param
  }

  def getBoolParam(params: Map[String, String], key: String) : Boolean = {
    val param = getParam(params, key)
    if (param == "true") {
      true
    }
    else if(param == "false"){
      false
    } else {
      throw new IllegalArgumentException("Parameter " + key + " must be either \"true\" or \"false\"")
    }
  }
}

abstract class Device() extends Module() {
  // val io = IO(new InternalIO()) // Nested IO wrapping gives issues. As io is overriden alsways it has been commented out
}

class InternalIO() extends Bundle() with patmos.HasSuperMode

abstract class CoreDevice() extends Device() {
  val io: CoreDeviceIO
}

class CoreDeviceIO() extends InternalIO() {
  val ocp = new OcpCoreSlavePort(ADDR_WIDTH, DATA_WIDTH)
}

abstract class IODevice() extends Device() {
  val io: IODeviceIO
}

class IODeviceIO() extends InternalIO() {
  val ocp = new OcpIOSlavePort(ADDR_WIDTH, DATA_WIDTH)
}

abstract class BurstDevice(addrBits: Int) extends Device() {
  val io: BurstDeviceIO
}

class BurstDeviceIO(val addrBits: Int) extends InternalIO() {
  val ocp = new OcpBurstSlavePort(addrBits, DATA_WIDTH, BURST_LENGTH)
}
