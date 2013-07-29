/**
 * Copyright 2013 MIT Libraries
 * Licensed under: http://www.apache.org/licenses/LICENSE-2.0
 */

package edu.mit.lib.resync

import org.scalatest.FunSuite

class ResourceSyncTest extends FunSuite {

	test("Enumerations should resolve correctly") {
		assert(Frequency.always.toString === "always")
		assert(Capability.resourcelist.toString === "resourcelist")
	}
}
