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

import org.neo4j.graphdb.Node
import collection.Seq

class PatternMatcher(startPoint: PatternNode, bindings: Map[String, MatchingPair]) extends Traversable[Map[String, Any]] {

  def foreach[U](f: (Map[String, Any]) => U) {
    traverseNode(MatchingPair(startPoint, startPoint.pinnedEntity.get), Seq(), bindings.values.toSeq, f)
  }

  private def traverseNode[U](current: MatchingPair,
                              history: Seq[MatchingPair],
                              remaining: Seq[MatchingPair],
                              yielder: Map[String, Any] => U): Boolean = {
    debug(current, history, remaining)

    val (pNode, gNode) = current.getPatternAndGraphPoint

    bindings.get(pNode.key) match {
      case Some(pinnedNode) => if (pinnedNode.entity != gNode) return false
      case None =>
    }

    val notYetVisited = pNode.getPRels(history).toList

    if (notYetVisited.isEmpty) {
      traverseNextNodeOrYield(remaining, history ++ Seq(current), yielder)
    } else {
      /*
      We only care about the first pattern relationship. We'll add this current position
      in future, so that remaining pattern relationships can be traversed.
      */

      val mostMarkedRel = getMostMarkedRelationship(notYetVisited)

      traverseRelationship(current, mostMarkedRel, history, remaining ++ Seq(current), yielder)
    }
  }

  private def traverseRelationship[U](currentNode: MatchingPair,
                                      currentRel: PatternRelationship,
                                      history: Seq[MatchingPair],
                                      remaining: Seq[MatchingPair],
                                      yielder: (Map[String, Any]) => U): Boolean = {
    debug(currentNode, currentRel, history, remaining)

    val (pNode, gNode) = currentNode.getPatternAndGraphPoint

    val notVisitedRelationships = currentNode.getGraphRelationships(currentRel, history)

    val nextPNode = currentRel.getOtherNode(pNode)

    /*
    We need to know if any of these sub-calls results in a yield. If none do, and we're
    looking at an optional pattern relationship, we'll output a null as match.
     */
    val yielded = notVisitedRelationships.map(rel => {
      val nextNode = rel.getOtherNode(gNode)
      val newHistory = history ++ Seq(currentNode, MatchingPair(currentRel, rel))
      traverseNode(MatchingPair(nextPNode, nextNode), newHistory, remaining, yielder)
    }).foldLeft(false)(_ || _)

    if (yielded) {
      return true
    }

    if (currentRel.optional) {
      return traverseNextNodeOrYield(remaining, history ++ Seq(currentNode, MatchingPair(currentRel, null)), yielder)
    }

    markPathToBoundNode(currentNode, currentRel)
    false
  }

  private def markPathToBoundNode(matchingNode:MatchingPair, rel:PatternRelationship) {
    val node = matchingNode.patternElement.asInstanceOf[PatternNode]
    val startNodes = bindings.values.filter(_.patternElement.isInstanceOf[PatternNode]).map(_.patternElement.asInstanceOf[PatternNode]).toSeq
    val paths = shortestPaths(node, startNodes)

    //Mark all nodes and relationships in all paths leading to a start node

    paths.foreach( _.foreach( _.mark(10) ))
    rel.mark(10)
  }

  private def traverseNextNodeOrYield[U](remaining: Seq[MatchingPair], history: Seq[MatchingPair], yielder: Map[String, Any] => U): Boolean = {
    debug(history, remaining)

    if (remaining.isEmpty) {
      yieldThis(yielder, history)
      true
    }
    else {
      traverseNode(remaining.head, history, remaining.tail, yielder)
    }
  }

  def extractResultMap[U](history: scala.Seq[MatchingPair]): Map[String, Object] = {
    history.flatMap(_ match {
      case MatchingPair(p, e) => (p, e) match {
        case (pe: PatternNode, entity: Node) => Seq(pe.key -> entity)
        case (pe: PatternRelationship, entity: SingleGraphRelationship) => Seq(pe.key -> entity.rel)
        case (pe: PatternRelationship, null) => Seq(pe.key -> null)
        case (pe: VariableLengthPatternRelationship, entity: VariableLengthGraphRelationship) => Seq(
          pe.start.key -> entity.path.startNode(),
          pe.end.key -> entity.path.endNode(),
          pe.key -> entity.path
        )
      }
    }).toMap
  }

  private def yieldThis[U](yielder: Map[String, Any] => U, history: Seq[MatchingPair]) {
    val resultMap = extractResultMap(history)
    debug(history, resultMap)

    yielder(resultMap)
  }

  def getMostMarkedRelationship(notYetVisited: List[PatternRelationship]) = notYetVisited.reduceLeft((a, b) => if (a.compare(b) > 0) a else b)

  val isDebugging = false

  def debug[U](history: Seq[MatchingPair], remaining: Seq[MatchingPair]) {
    if (isDebugging)
      println(String.format("""traverseNextNodeOrYield
      history=%s
      remaining=%s)""", history, remaining))
  }

  def debug[U](current: MatchingPair, history: Seq[MatchingPair], remaining: Seq[MatchingPair]) {
    if (isDebugging)
      println(String.format("""traverseNode
    current=%s
    history=%s
    remaining=%s
    """, current, history, remaining))
  }

  def debug[U](current: MatchingPair, pRel: PatternRelationship, history: Seq[MatchingPair], remaining: Seq[MatchingPair]) {
    if (isDebugging)
      println(String.format("""traverseRelationship
    current=%s
    pRel=%s
    history=%s
    remaining=%s
    """, current, pRel, history, remaining))
  }

  def debug[U](history: Seq[MatchingPair], resultMap: Map[String, Object]) {
    if (isDebugging)
      println(String.format("""yield(history=%s) => %s
    """, history, resultMap))
  }

}