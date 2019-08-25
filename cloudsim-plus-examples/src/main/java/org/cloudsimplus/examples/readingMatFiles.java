package org.cloudsimplus.examples;

import com.jmatio.io.MatFileReader;
import com.jmatio.types.MLDouble;

import java.io.IOException;
import java.util.Arrays;



public class readingMatFiles {
    public readingMatFiles() {
    }

    public static void main(String argv[]) throws IOException {

        // Reading training data (independent variable matrix)
        MatFileReader matfilereader1 = new MatFileReader("/home/humaira/Repositories/cloudsim-plus/cloudsim-plus-examples/src/X.mat");
        double[][] trainx = ((MLDouble) matfilereader1.getMLArray("X")).getArray();
        System.out.print(trainx);

        // Reading training data (dependent variable matrix)
        MatFileReader matfilereader2 = new MatFileReader("/home/humaira/Repositories/cloudsim-plus/cloudsim-plus-examples/src/Y.mat");
        double[][] single = ((MLDouble)matfilereader2.getMLArray("Y")).getArray();
        double[] trainy = Arrays.stream(single) //Creates a Stream<double[]>
            .flatMapToDouble(Arrays::stream) //merges the arrays into a DoubleStream
            .toArray(); //collects everything into a double[] array
        //System.out.print(trainy);

    }

}

