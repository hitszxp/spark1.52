/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql

import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.expressions.{GenericRow, GenericRowWithSchema}
import org.apache.spark.sql.types._
import org.scalatest.{Matchers, FunSpec}

class RowTest extends FunSpec with Matchers {

  val schema = StructType(
    StructField("col1", StringType) ::
    StructField("col2", StringType) ::
    StructField("col3", IntegerType) :: Nil)//列表结尾为Nil
  val values = Array("value1", "value2", 1)

  val sampleRow: Row = new GenericRowWithSchema(values, schema)
  val noSchemaRow: Row = new GenericRow(values)
  //行(无模式)
  describe("Row (without schema)") {
    //通过fieldName访问时会抛出异常
    it("throws an exception when accessing by fieldName") {
      intercept[UnsupportedOperationException] {
        noSchemaRow.fieldIndex("col1")
      }
      intercept[UnsupportedOperationException] {
        noSchemaRow.getAs("col1")
      }
    }
  }
  //行(带模式)
  describe("Row (with schema)") {
    //fieldIndex（name）返回字段索引
    it("fieldIndex(name) returns field index") {
      sampleRow.fieldIndex("col1") shouldBe 0
      sampleRow.fieldIndex("col3") shouldBe 2
    }
    //getAs [T]通过字段名检索值
    it("getAs[T] retrieves a value by fieldname") {
      sampleRow.getAs[String]("col1") shouldBe "value1"
      sampleRow.getAs[Int]("col3") shouldBe 1
    }
    //访问不存在的字段会引发异常
    it("Accessing non existent field throws an exception") {
      intercept[IllegalArgumentException] {
        sampleRow.getAs[String]("non_existent")
      }
    }
    //getValuesMap()检索多个字段的值作为Map(field -> value)
    it("getValuesMap() retrieves values of multiple fields as a Map(field -> value)") {
      val expected = Map(
        "col1" -> "value1",
        "col2" -> "value2"
      )
      sampleRow.getValuesMap(List("col1", "col2")) shouldBe expected
    }
  }
  //行等于
  describe("row equals") {
    val externalRow = Row(1, 2)
    val externalRow2 = Row(1, 2)
    val internalRow = InternalRow(1, 2)
    val internalRow2 = InternalRow(1, 2)
    //外部行的等式检查
    it("equality check for external rows") {
      externalRow shouldEqual externalRow2
    }
    //相等检查内部行
    it("equality check for internal rows") {
      internalRow shouldEqual internalRow2
    }
  }
}
