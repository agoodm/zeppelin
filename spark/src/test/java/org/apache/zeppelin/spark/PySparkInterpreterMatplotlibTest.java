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

package org.apache.zeppelin.spark;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterContextRunner;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterOutputListener;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Type;
import org.apache.zeppelin.resource.LocalResourcePool;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Properties;

import static org.junit.Assert.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PySparkInterpreterMatplotlibTest {
  public static SparkInterpreter sparkInterpreter;
  public static PySparkInterpreter pyspark;
  public static InterpreterGroup intpGroup;
  private File tmpDir;
  public static Logger LOGGER = LoggerFactory.getLogger(PySparkInterpreterTest.class);
  private InterpreterContext context;
  
  public static class AltPySparkInterpreter extends PySparkInterpreter {
    /**
     * Since pyspark output is  sent to an outputstream rather than
     * being directly provided by interpret(), this subclass is created to
     * override interpret() to append the result from the outputStream
     * for the sake of convenience in testing. 
     */
    public AltPySparkInterpreter(Properties property) {
      super(property);
    }

    /**
     * This code is mainly copied from RemoteInterpreterServer.java which 
     * normally handles this in real use cases.
     */    
    @Override
    public InterpreterResult interpret(String st, InterpreterContext context) {
      InterpreterResult result = super.interpret(st, context);
      String message = "";
      Type outputType;
      byte[] interpreterOutput;
      try {
        context.out.flush();
        outputType = context.out.getType();
        interpreterOutput = context.out.toByteArray();
      } catch (IOException e) {
        throw new InterpreterException(e);
      }
      

      if (interpreterOutput != null && interpreterOutput.length > 0) {
        message = new String(interpreterOutput);
      }

      String interpreterResultMessage = result.message();

      InterpreterResult combinedResult;
      if (interpreterResultMessage != null && !interpreterResultMessage.isEmpty()) {
        message += interpreterResultMessage;
        combinedResult = new InterpreterResult(result.code(), result.type(), message);
      } else {
        combinedResult = new InterpreterResult(result.code(), outputType, message);
      }

      return combinedResult;      
    }
  }

  public static Properties getPySparkTestProperties() {
    Properties p = new Properties();
    p.setProperty("master", "local[*]");
    p.setProperty("spark.app.name", "Zeppelin Test");
    p.setProperty("zeppelin.spark.useHiveContext", "true");
    p.setProperty("zeppelin.spark.maxResult", "1000");
    p.setProperty("zeppelin.spark.importImplicit", "true");
    p.setProperty("zeppelin.pyspark.python", "python");
    return p;
  }

  /**
   * Get spark version number as a numerical value.
   * eg. 1.1.x => 11, 1.2.x => 12, 1.3.x => 13 ...
   */
  public static int getSparkVersionNumber() {
    if (sparkInterpreter == null) {
      return 0;
    }

    String[] split = sparkInterpreter.getSparkContext().version().split("\\.");
    int version = Integer.parseInt(split[0]) * 10 + Integer.parseInt(split[1]);
    return version;
  }

  @Before
  public void setUp() throws Exception {
    tmpDir = new File(System.getProperty("java.io.tmpdir") + "/ZeppelinLTest_" + System.currentTimeMillis());
    System.setProperty("zeppelin.dep.localrepo", tmpDir.getAbsolutePath() + "/local-repo");
    tmpDir.mkdirs();

    intpGroup = new InterpreterGroup();
    intpGroup.put("note", new LinkedList<Interpreter>());

    if (sparkInterpreter == null) {
      sparkInterpreter = new SparkInterpreter(getPySparkTestProperties());
      intpGroup.get("note").add(sparkInterpreter);
      sparkInterpreter.setInterpreterGroup(intpGroup);
      sparkInterpreter.open();
    }

    if (pyspark == null) {
      pyspark = new AltPySparkInterpreter(getPySparkTestProperties());
      intpGroup.get("note").add(pyspark);
      pyspark.setInterpreterGroup(intpGroup);
      pyspark.open();
    }

    context = new InterpreterContext("note", "id", "title", "text",
      new AuthenticationInfo(),
      new HashMap<String, Object>(),
      new GUI(),
      new AngularObjectRegistry(intpGroup.getId(), null),
      new LocalResourcePool("id"),
      new LinkedList<InterpreterContextRunner>(),
      new InterpreterOutput(new InterpreterOutputListener() {
        @Override
        public void onAppend(InterpreterOutput out, byte[] line) {

        }

        @Override
        public void onUpdate(InterpreterOutput out, byte[] output) {

        }
      }));
  }

  @After
  public void tearDown() throws Exception {
    delete(tmpDir);
  }

  private void delete(File file) {
    if (file.isFile()) file.delete();
    else if (file.isDirectory()) {
      File[] files = file.listFiles();
      if (files != null && files.length > 0) {
        for (File f : files) {
          delete(f);
        }
      }
      file.delete();
    }
  }

  @Test
  public void dependenciesAreInstalled() {
    // matplotlib
    InterpreterResult ret = pyspark.interpret("import matplotlib", context);
    assertEquals(ret.message(), InterpreterResult.Code.SUCCESS, ret.code());
    
    // inline backend
    ret = pyspark.interpret("import backend_zinline", context);
    assertEquals(ret.message(), InterpreterResult.Code.SUCCESS, ret.code());
  }

  @Test
  public void showPlot() {
    // Simple plot test
    InterpreterResult ret;
    ret = pyspark.interpret("import matplotlib.pyplot as plt", context);
    ret = pyspark.interpret("plt.close()", context);
    ret = pyspark.interpret("z.configure_mpl(interactive=False)", context);
    ret = pyspark.interpret("plt.plot([1, 2, 3])", context);
    ret = pyspark.interpret("plt.show()", context);

    assertEquals(ret.message(), InterpreterResult.Code.SUCCESS, ret.code());
    assertEquals(ret.message(), Type.HTML, ret.type());
    assertTrue(ret.message().contains("data:image/png;base64"));
    assertTrue(ret.message().contains("<div>"));
  }

  @Test
  // Test for when configuration is set to auto-close figures after show().
  public void testClose() {
    InterpreterResult ret;
    InterpreterResult ret1;
    InterpreterResult ret2;
    ret = pyspark.interpret("import matplotlib.pyplot as plt", context);
    ret = pyspark.interpret("plt.close()", context);
    ret = pyspark.interpret("z.configure_mpl(interactive=False, close=True, angular=False)", context);
    ret = pyspark.interpret("plt.plot([1, 2, 3])", context);
    ret1 = pyspark.interpret("plt.show()", context);
    
    // Second call to show() should print nothing, and Type should be TEXT.
    // This is because when close=True, there should be no living instances
    // of FigureManager, causing show() to return before setting the output
    // type to HTML.
    ret = pyspark.interpret("plt.show()", context);
    assertEquals(ret.message(), InterpreterResult.Code.SUCCESS, ret.code());
    assertEquals(ret.message(), Type.TEXT, ret.type());
    assertTrue(ret.message().equals(""));
    
    // Now test that new plot is drawn. It should be identical to the
    // previous one.
    ret = pyspark.interpret("plt.plot([1, 2, 3])", context);
    ret2 = pyspark.interpret("plt.show()", context);
    assertTrue(ret1.message().equals(ret2.message()));
  }
  
  @Test
  // Test for when configuration is set to not auto-close figures after show().
  public void testNoClose() {
    InterpreterResult ret;
    InterpreterResult ret1;
    InterpreterResult ret2;
    ret = pyspark.interpret("import matplotlib.pyplot as plt", context);
    ret = pyspark.interpret("plt.close()", context);
    ret = pyspark.interpret("z.configure_mpl(interactive=False, close=False, angular=False)", context);
    ret = pyspark.interpret("plt.plot([1, 2, 3])", context);
    ret1 = pyspark.interpret("plt.show()", context);
    
    // Second call to show() should print nothing, and Type should be HTML.
    // This is because when close=False, there should be living instances
    // of FigureManager, causing show() to set the output
    // type to HTML even though the figure is inactive.
    ret = pyspark.interpret("plt.show()", context);
    assertEquals(ret.message(), InterpreterResult.Code.SUCCESS, ret.code());
    assertEquals(ret.message(), Type.HTML, ret.type());
    assertTrue(ret.message().equals(""));
    
    // Now test that plot can be reshown if it is updated. It should be
    // different from the previous one because it will plot the same line
    // again but in a different color.
    ret = pyspark.interpret("plt.plot([1, 2, 3])", context);
    ret2 = pyspark.interpret("plt.show()", context);
    assertTrue(!ret1.message().equals(ret2.message()));
  }
  
  @Test
  // Test angular mode
  public void testAngular() {
    InterpreterResult ret;
    ret = pyspark.interpret("import matplotlib.pyplot as plt", context);
    ret = pyspark.interpret("plt.close()", context);
    ret = pyspark.interpret("z.configure_mpl(interactive=False, close=False, angular=True)", context);
    ret = pyspark.interpret("plt.plot([1, 2, 3])", context);
    ret = pyspark.interpret("plt.show()", context);    
    assertEquals(ret.message(), InterpreterResult.Code.SUCCESS, ret.code());
    assertEquals(ret.message(), Type.ANGULAR, ret.type());

    // Check if the figure data is in the Angular Object Registry
    AngularObjectRegistry registry = context.getAngularObjectRegistry();
    String figureData = registry.getAll("note", null).get(1).toString();
    assertTrue(ret.message().contains(figureData));
  }  
}
