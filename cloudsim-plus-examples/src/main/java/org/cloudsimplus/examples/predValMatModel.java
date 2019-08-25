package org.cloudsimplus.examples;


//package smile.regression;

import com.jmatio.io.MatFileReader;
import com.jmatio.types.MLDouble;
//import home.humaira.Repositories.smile.*;
//import smile.math.kernel.GaussianKernel;
//import smile.validation.LOOCV;

import com.mathworks.toolbox.javabuilder.MWArray;
import predictingFunctionJava.*;

import java.io.IOException;
import java.util.Arrays;



public class predValMatModel {
    public predValMatModel() {
    }

    public static void main(String argv[]) throws IOException {
        Class1 predFunc= null;
        Object[] result = null;
        double[] testData = {3705.63270793333,1224.84051161667,2450.62256268333,6269.09566101667};//new double[4];
//        testData = 3705.63270793333,1224.84051161667,2450.62256268333,6269.09566101667;

        try
        {
            predFunc = new Class1();

            result = predFunc.predictingFunctionJava(1, testData);
            System.out.println("Length of result is: "+result.length);
            System.out.println("finished try block");
//
//            n = new MWNumericArray(Double.valueOf(testData, DOUBLE);
//            MatFileReader matfilereader2 = new MatFileReader("/home/humaira/Repositories/MakeSquare/Y.mat");
//            double[][] single = ((MLDouble)matfilereader2.getMLArray("Y")).getArray();
//            double[] trainy = Arrays.stream(single) //Creates a Stream<double[]>
//                    .flatMapToDouble(Arrays::stream) //merges the arrays into a DoubleStream
//                    .toArray(); //collects everything into a double[] array
////            //System.out.print(trainy);
//            double[] trainy = {0.100447633333333, 0.0889426500000000,	4944.07861236667,	1249.13715138333} ;
//                    ;
//            result = predFunc.predictingFunctionJava(0);
//            System.out.println(result[0]);
//            String stringArray[] = Arrays.asList(result[0])
//                .toArray(new String[0]);
            // .toArray(new String[objectArray.length]);
            System.out.println(result[0]);

//            System.out.println(Arrays.toString(stringArray));


        }
        catch (Exception e)
        {
            System.out.println("Exception: " + e.toString());
        }
        finally
        {
            MWArray.disposeArray(result);

//            System.out.println(result);

//            MWArray.disposeArray(n);
//            MWArray.disposeArray(result);
//            predFunc.dispose();
        }

    }

}


