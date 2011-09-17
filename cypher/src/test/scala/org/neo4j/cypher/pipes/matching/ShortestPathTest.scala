/**
 * Copyright (c) 2002-2011 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.pipes.matching

import org.neo4j.cypher.GraphDatabaseTestBase
import org.scalatest.Assertions
import org.junit.{Before, Test}
import org.neo4j.graphdb.Direction

class ShortestPathTest extends GraphDatabaseTestBase with Assertions {

  var a: PatternNode = null
  var b: PatternNode = null
  var c: PatternNode = null
  var d: PatternNode = null
  var e: PatternNode = null
  var f: PatternNode = null

  @Before def init() {
    a = new PatternNode("a")
    b = new PatternNode("b")
    c = new PatternNode("c")
    d = new PatternNode("d")
    e = new PatternNode("e")
    f = new PatternNode("f")
  }

  @Test def shouldReturnEndNodeIfItIsInStartNodeSet() {
    assert(Seq(Seq(a)) === shortestPaths(a, Seq(a)))
  }

  @Test def shouldReturnANodeThatIsDirectlyConnected() {
    val r1 = a.relateTo("r1", b, None, Direction.OUTGOING, false)
    assert(Seq(Seq(a, r1, b)) === shortestPaths(a, Seq(b)))
  }

  @Test def LshapesShouldNotBeAProblem() {
    val r1 = a.relateTo("r1", b, None, Direction.OUTGOING, false)
    val r2 = b.relateTo("r1", c, None, Direction.OUTGOING, false)
    c.relateTo("r1", d, None, Direction.OUTGOING, false)
    d.relateTo("r1", e, None, Direction.OUTGOING, false)

    assert(Seq(Seq(a, r1, b, r2, c)) === shortestPaths(a, Seq(c)))
  }

  @Test def twoPathsAreAlsoCorrect() {
    val r1 = a.relateTo("r1", b, None, Direction.OUTGOING, false)
    val r2 = b.relateTo("r2", c, None, Direction.OUTGOING, false)
    val r3 = c.relateTo("r3", d, None, Direction.OUTGOING, false)
    val r4 = d.relateTo("r4", e, None, Direction.OUTGOING, false)

    assert(Set(Seq(c, r2, b, r1, a), Seq(c, r3, d, r4, e)) === shortestPaths(c, Seq(a, e)).toSet)
  }
}