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

import org.apache.spark.sql.test.SharedSQLContext
import org.apache.spark.sql.test.SQLTestData._

private case class FunctionResult(f1: String, f2: String)
/**
 * 自定义函数测试
 */
class UDFSuite extends QueryTest with SharedSQLContext {
  //导入隐式转换  
  import testImplicits._

  test("built-in fixed arity expressions") {//内置固定数量的表达
    val df = ctx.emptyDataFrame    
    /**
      +-------+--------+--------+----------+
      |'rand()|'randn()|'rand(5)|'randn(50)|
      +-------+--------+--------+----------+
      +-------+--------+--------+----------+*/
    df.selectExpr("rand()", "randn()", "rand(5)", "randn(50)").show()
  }

  test("built-in vararg expressions") {//内置可变参数的表达式
    val df = Seq((1, 2)).toDF("a", "b")  
    df.selectExpr("array(a, b)").show()
      /**
     * +-----------+
        |'array(a,b)|
        +-----------+
        |     [1, 2]|
        +-----------+
     */   
    df.selectExpr("struct(a, b)").show()
     /**
     * +------------+
       |'struct(a,b)|
       +------------+
       |       [1,2]|
       +------------+
     */
  }
  //内置表达式的多个构造函数
  test("built-in expressions with multiple constructors") {
    val df = Seq(("abcd", 2)).toDF("a", "b")
    //[abcd,2]
    df.collect().foreach(println)
    //substr(a, 2)截取字段a,从第二位开始[bcd]
    //substr(a, 2, 3)[bcd]截取字段a,从第二位开始,截取长度3
    df.selectExpr("substr(a, 2)", "substr(a, 2, 3)").collect().foreach {println}
  }

  test("count") {//计数
    val df = Seq(("abcd", 2)).toDF("a", "b")
    df.selectExpr("count(a)").show()
    /** +---------+
        |'count(a)|
        +---------+
        |        1|
        +---------+**/
  }

  test("count distinct") {//重复计数
    val df = Seq(("abcd", 2)).toDF("a", "b")
    df.selectExpr("count(distinct a)").show()
    /**
     * +-----------------+
       |COUNT(DISTINCT a)|
       +-----------------+
       |                1|
       +-----------------+
     */
  }

  test("SPARK-8003 spark_partition_id") {
    val df = Seq((1, "Tearing down the walls that divide us")).toDF("id", "saying")
    //注册一个临时表
    df.registerTempTable("tmp_table")
    //获得Spark分区ID
    sql("select spark_partition_id() from tmp_table").toDF().show()
   /** 	+---+
        |_c0|
        +---+
        |  0|
        +---+**/
    checkAnswer(sql("select spark_partition_id() from tmp_table").toDF(), Row(0))
    ctx.dropTempTable("tmp_table")
  }

  test("SPARK-8005 input_file_name") {//输入文件名
    withTempPath { dir =>
      val data = ctx.sparkContext.parallelize(0 to 10, 2).toDF("id")
      data.write.parquet(dir.getCanonicalPath)
      ctx.read.parquet(dir.getCanonicalPath).registerTempTable("test_table")
      val answer = sql("select input_file_name() from test_table").head().getString(0)
      //println(answer)
      //assert(answer.contains(dir.getCanonicalPath))
      /**
       *  +--------------------+
          |                 _c0|
          +--------------------+
          |file:/C:/Users/li...|
          |file:/C:/Users/li...|
          |file:/C:/Users/li...|
          |file:/C:/Users/li...|
          |file:/C:/Users/li...|
          |file:/C:/Users/li...|
          |file:/C:/Users/li...|
          |file:/C:/Users/li...|
          |file:/C:/Users/li...|
          |file:/C:/Users/li...|
          |file:/C:/Users/li...|
          +--------------------+*/
      sql("select input_file_name() from test_table").show()
      
      assert(sql("select input_file_name() from test_table").distinct().collect().length >= 2)
      ctx.dropTempTable("test_table")
    }
  }

  test("error reporting for incorrect number of arguments") {//错误报告参数不正确数字
    val df = ctx.emptyDataFrame
    val e = intercept[AnalysisException] {
      df.selectExpr("substr('abcd', 2, 3, 4)")
    }
    assert(e.getMessage.contains("arguments"))
  }

  test("error reporting for undefined functions") {//错误报告未定义的函数
    val df = ctx.emptyDataFrame
    val e = intercept[AnalysisException] {
      df.selectExpr("a_function_that_does_not_exist()")
    }
    assert(e.getMessage.contains("undefined function"))
  }

  test("Simple UDF") {//简单的自定义函数
    ctx.udf.register("strLenScala", (_: String).length)
    assert(sql("SELECT strLenScala('test')").head().getInt(0) === 4)
  }

  test("ZeroArgument UDF") {//没有参数的自定义函数
    ctx.udf.register("random0", () => { Math.random()})
    assert(sql("SELECT random0()").head().getDouble(0) >= 0.0)
  }

  test("TwoArgument UDF") {//两个参数的自定义函数
    ctx.udf.register("strLenScala", (_: String).length + (_: Int))
    assert(sql("SELECT strLenScala('test', 1)").head().getInt(0) === 5)
  }

  test("UDF in a WHERE") {//自定义函数在一个WHERE
    ctx.udf.register("oneArgFilter", (n: Int) => { n > 80 })

    val df = ctx.sparkContext.parallelize(
      (1 to 100).map(i => TestData(i, i.toString))).toDF()
    df.registerTempTable("integerData")

    val result =
      sql("SELECT * FROM integerData WHERE oneArgFilter(key)")
    assert(result.count() === 20)
  }

  test("UDF in a HAVING") {//自定义函数在一个HAVING
    ctx.udf.register("havingFilter", (n: Long) => { n > 5 })

    val df = Seq(("red", 1), ("red", 2), ("blue", 10),
      ("green", 100), ("green", 200)).toDF("g", "v")
    df.registerTempTable("groupData")

    val result =
      sql(
        """
         | SELECT g, SUM(v) as s
         | FROM groupData
         | GROUP BY g
         | HAVING havingFilter(s)
        """.stripMargin)

    assert(result.count() === 2)
  }

  test("UDF in a GROUP BY") {//自定义函数在一个GROUP BY
    ctx.udf.register("groupFunction", (n: Int) => { n > 10 })

    val df = Seq(("red", 1), ("red", 2), ("blue", 10),
      ("green", 100), ("green", 200)).toDF("g", "v")
    df.registerTempTable("groupData")

    val result =
      sql(
        """
         | SELECT SUM(v)
         | FROM groupData
         | GROUP BY groupFunction(v)
        """.stripMargin)
    assert(result.count() === 2)
  }

  test("UDFs everywhere") {//任何使用定义函数
    ctx.udf.register("groupFunction", (n: Int) => { n > 10 })
    ctx.udf.register("havingFilter", (n: Long) => { n > 2000 })
    ctx.udf.register("whereFilter", (n: Int) => { n < 150 })
    ctx.udf.register("timesHundred", (n: Long) => { n * 100 })

    val df = Seq(("red", 1), ("red", 2), ("blue", 10),
      ("green", 100), ("green", 200)).toDF("g", "v")
    df.registerTempTable("groupData")

    val result =
      sql(
        """
         | SELECT timesHundred(SUM(v)) as v100
         | FROM groupData
         | WHERE whereFilter(v)
         | GROUP BY groupFunction(v)
         | HAVING havingFilter(v100)
        """.stripMargin)
    assert(result.count() === 1)
  }

  test("struct UDF") {//结构定义函数
    ctx.udf.register("returnStruct", (f1: String, f2: String) => FunctionResult(f1, f2))

    val result =
      sql("SELECT returnStruct('test', 'test2') as ret")
        .select($"ret.f1").head().getString(0)
    assert(result === "test")
  }

  test("udf that is transformed") {//转换自义函数
    ctx.udf.register("makeStruct", (x: Int, y: Int) => (x, y))
    // 1 + 1 is constant folded causing a transformation.
    //常数折叠引起变换
    assert(sql("SELECT makeStruct(1 + 1, 2)").first().getAs[Row](0) === Row(2, 2))
  }

  test("type coercion for udf inputs") {//UDF程序输入类型强制
    ctx.udf.register("intExpected", (x: Int) => x)
    // pass a decimal to intExpected.
    assert(sql("SELECT intExpected(1.0)").head().getInt(0) === 1)
  }
}
