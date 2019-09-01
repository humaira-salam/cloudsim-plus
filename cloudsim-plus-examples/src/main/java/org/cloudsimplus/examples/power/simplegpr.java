package org.cloudsimplus.examples.power;

import com.mathworks.toolbox.javabuilder.MWArray;
import com.mathworks.toolbox.javabuilder.MWClassID;
import com.mathworks.toolbox.javabuilder.MWNumericArray;
//import makesqr.Class1;
import com.mathworks.toolbox.javabuilder.*;
import makesqr.*;
import com.jmatio.io.*;
import com.jmatio.types.*;
import GPRjava.Class1;


public class simplegpr {

    public static void main(String[] args)
    {
        MWNumericArray n = null;
        Object[] result = null;
        Class1 predPow = null;
        double[] testData;
//        if (args.length == 0)
//        {
//            System.out.println("Error: must input a positive integer");
//            return;
//        }

        try
        {
//            n = new MWNumericArray(Double.valueOf(args[0]),
//                MWClassID.DOUBLE);
            testData = new double[]{3710.63297271667};
            predPow = new Class1();

            result = predPow.GPRjava(1, testData);
            System.out.println(result[0]);


        }
        catch (Exception e)
        {
            System.out.println("Exception: " + e.toString());
        }
        finally
        {
            MWArray.disposeArray(n);
            MWArray.disposeArray(result);
            predPow.dispose();
        }

    }
}
