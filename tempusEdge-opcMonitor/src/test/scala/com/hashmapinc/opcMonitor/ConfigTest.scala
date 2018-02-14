package com.hashmapinc.opcMonitor

import org.scalatest.FlatSpec

class ConfigTest extends FlatSpec {
  //===========================================================================
  // Test the context attribute
  //===========================================================================
  "context" should "be 'production' by default" in {
    val defaultContext = "production"
    assert(Config.context == defaultContext)
  }

  it should "be 'test' after initializing the test context" in {
    Config.initTestContext(null)
    val testContext = "test"
    assert(Config.context == testContext)
  }//==========================================================================


  //===========================================================================
  // Test the update functionality 
  //===========================================================================
  "Config" should "start with no config" in {
    assert(!Config.iofogConfig.isDefined)
  }

  it should "have configs after being assigned configs" in {
    //TODO: Implement this
    assert(true)
  }//==========================================================================
}