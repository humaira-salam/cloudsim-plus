

package org.cloudsimplus.examples.power;

import ch.qos.logback.classic.Level;
import com.mathworks.toolbox.javabuilder.MWArray;
import com.opencsv.CSVWriter;
import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.brokers.DatacenterBroker;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.power.models.PowerAware;
import org.cloudbus.cloudsim.power.models.*;
//import org.cloudbus.cloudsim.power.models.PowerModelLinear;
//import org.cloudbus.cloudsim.power.models.PowerModelSqrt;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.ResourceProvisioner;
import org.cloudbus.cloudsim.provisioners.ResourceProvisionerSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.schedulers.vm.VmScheduler;
import org.cloudbus.cloudsim.schedulers.vm.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModel;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelFull;
import org.cloudbus.cloudsim.vms.UtilizationHistory;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.examples.resourceusage.VmsRamAndBwUsageExample;
import org.cloudsimplus.util.Log;
import org.math.plot.Plot2DPanel;
import predictingFunctionJava.Class1;

import java.io.FileWriter;
import java.lang.reflect.Array;
import com.jmatio.io.MatFileReader;
import com.jmatio.types.MLDouble;

import java.io.IOException;
import java.util.Arrays;

import java.util.*;
import java.util.stream.IntStream;

public class PowerModelComparison {

    private static final int HOST_PES = 4;


    /**
     * Defines the minimum percentage of power a Host uses,
     * even it it's idle.
     */
    private static final double STATIC_POWER_PERCENT = 0.3764;

    /**
     * The max number of watt-second (Ws) of power a Host uses.
     */
    private static final int MAX_POWER_WATTS_SEC = 83;
    private double[][] powValSim;
    private double[] powPredDb ;

    /**
     * If set to false, consecutive lines with the the same CPU utilization and power consumption
     * will be shown only once, at the time that such metrics started to return those values.
     * The last history line is always shown, independent of any condition.
     */
    private boolean showAllHostUtilizationHistoryEntries;


    public static void main(String[] args) throws IOException {

        // Reading true power values
        MatFileReader powMeasMed = new MatFileReader("/home/humaira/Repositories/cloudsim-plus/cloudsim-plus-examples/src/pow_meas_med.mat");
        double[][] pow_meas_med = ((MLDouble) powMeasMed.getMLArray("pow_meas_med")).getArray();
        System.out.print(pow_meas_med);


        // Reading vms_ins_med
        MatFileReader vmsInsMed = new MatFileReader("/home/humaira/Repositories/cloudsim-plus/cloudsim-plus-examples/src/vms_ins_med.mat");
        double[][] vms_ins_med = ((MLDouble) vmsInsMed.getMLArray("vms_ins_med")).getArray();
        System.out.print(vms_ins_med);

        // Reading all_ins_med
        MatFileReader allInsMed = new MatFileReader("/home/humaira/Repositories/cloudsim-plus/cloudsim-plus-examples/src/all_ins_med.mat");
        double[][] all_ins_med = ((MLDouble) allInsMed.getMLArray("all_ins_med")).getArray();
        System.out.print(all_ins_med);

        new PowerModelComparison(true, pow_meas_med, vms_ins_med, all_ins_med);


    }

    private PowerModelComparison(boolean showAllHostUtilizationHistoryEntries, double[][] pow_meas_med, double[][] vms_ins_med, double[][] all_ins_med) {
        getExistModPow(all_ins_med);
        getDynamicModelPower(vms_ins_med);
        powerModelsError(pow_meas_med);
    }


    private void getExistModPow(double[][] all_ins_med) {
        Host[] hostlist = new Host[4];
        //create host for each power model of cloudsim
        for(int i = 0; i < 4; i++){
            Host host = createPowerHost(i);
            hostlist[i] = host;
        }
//        Host host = createPowerHost();
        double[][]  hostIns= all_ins_med;
        int hostDataLen = hostIns.length; // for now take 1500 reading as others are not taken yet and are zero
        powValSim = new double[hostDataLen][4];
        // host maximum MIPs capacity
        double maxMipsHost = 163-84; // minus idle MIPs(84-90) ;
//        i <=hostIns.length
        int ind = 0;
        for(int i = 258 ; i < 1500; i++) {
            // get running MIPs of Host
            double[] val = hostIns[i];
            //get cpu utilization percentage from usage MIPs
            double cpuUtilPer = (val[0]-84) / maxMipsHost; // minus 84 (84-90) idle MIPss
            if(0 > cpuUtilPer && cpuUtilPer > 1){
                System.out.printf("error");
            }
            System.out.print(i + "\n");
            powValSim[ind][0] = hostlist[0].getPowerModel().getPower(cpuUtilPer);
            powValSim[ind][1] = hostlist[1].getPowerModel().getPower(cpuUtilPer);
            powValSim[ind][2] = hostlist[2].getPowerModel().getPower(cpuUtilPer);
            powValSim[ind][3] = hostlist[3].getPowerModel().getPower(cpuUtilPer);
            ind ++;
        }
    }



    private void getDynamicModelPower(double[][] vms_ins_med) {

        double[][]  vmsIns = vms_ins_med;
        int vmsDataLen = vmsIns.length; // for now take 1500 reading as others are not taken yet and are zero
        String[] powPredStr = new String[vmsDataLen];
        powPredDb = new double[vmsDataLen];
        Class1 powPredFunc= null;
        int ind = 0;
        Object[] powPred = null;
        double[] testData;
                try {
//                testData = new double[]{w, x, y, z};
                    powPredFunc = new Class1();
                    for(int i = 258 ; i < 1500; i++) {
                        testData = vmsIns[i];
//                        testData = new double[]{3725.36318716667, 2473.59933525000, 1213.89142435000, 0};
                        powPred = powPredFunc.predictingFunctionJava(1, testData);
                        powPredStr[ind] = String.valueOf(powPred[0]);

                        powPredDb[ind] =Double.parseDouble(powPredStr[ind]);
                        ind ++;
                        System.out.println("finished try block");
                        System.out.println(powPredDb[0]);
                    }
                } catch (Exception e) {
                    System.out.println("Exception: " + e.toString());
                } finally {
//                         Double resNum = (Double)powPred;
                    MWArray.disposeArray(powPredStr);
                }
    }

    private void powerModelsError(double[][] pow_meas_med) {
        double[][]  truePow = pow_meas_med;
        double[] truePowVal;
        double dynPowVal;
        Double[] dynPowErr = new Double[1500];
        double[][] csPowErr = new double[1500][4];

        // create FileWriter object with file as parameter
        FileWriter outputfile = null;
        try {
            outputfile = new FileWriter("/home/humaira/Repositories/resultsPower", true);
            // create CSVWriter with '|' as separator
            CSVWriter writer = new CSVWriter(outputfile, ',',
                CSVWriter.NO_QUOTE_CHARACTER,
                CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                CSVWriter.DEFAULT_LINE_END);

            int ind = 0;
            for(int i = 258 ; i < 1500; i++) {
                truePowVal = truePow[i];
//            double news = truePowVal[0];
//            dynPowVal = Double.parseDouble(powPredDb[0]);
                dynPowErr[ind] = Math.abs(powPredDb[ind] - truePowVal[0]);
                csPowErr[ind][0] = Math.abs(powValSim[ind][0] - truePowVal[0]);
                csPowErr[ind][1] = Math.abs(powValSim[ind][1] - truePowVal[0]);
                csPowErr[ind][2] = Math.abs(powValSim[ind][2] - truePowVal[0]);
                csPowErr[ind][3] = Math.abs(powValSim[ind][3] - truePowVal[0]);


            //write data to csv
            String[] data = {String.valueOf(truePowVal[0]), String.valueOf(dynPowErr[ind]), String.valueOf(csPowErr[ind][0]), String.valueOf(csPowErr[ind][1]), String.valueOf(csPowErr[ind][2]), String.valueOf(csPowErr[ind][3]) };
            writer.writeNext(data);

                ind++;
            }
            writer.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    private Host createPowerHost(int hostNum) {
        final List<Pe> peList = new ArrayList<>(HOST_PES);
        //List of Host's CPUs (Processing Elements, PEs)
        PowerModel powerModel;
        for (int i = 0; i < HOST_PES; i++) {
            peList.add(new PeSimple(6.33 * Math.pow(10, 3), new PeProvisionerSimple()));
        }

        if(hostNum == 1){
            powerModel = new PowerModelCubic(MAX_POWER_WATTS_SEC, STATIC_POWER_PERCENT);
        }
        else if(hostNum == 2){
            powerModel = new PowerModelSqrt(MAX_POWER_WATTS_SEC, STATIC_POWER_PERCENT);
        }
        else if(hostNum == 3){
            powerModel = new PowerModelSquare(MAX_POWER_WATTS_SEC, STATIC_POWER_PERCENT);
        }
        else{
            powerModel = new PowerModelLinear(MAX_POWER_WATTS_SEC, STATIC_POWER_PERCENT);
        }

        final long ram = 31000; //in Megabytes
        final long bw = 10000; //in Megabits/s
        final long storage = 916000; //in Megabytes
        final ResourceProvisioner ramProvisioner = new ResourceProvisionerSimple();
        final ResourceProvisioner bwProvisioner = new ResourceProvisionerSimple();
        final VmScheduler vmScheduler = new VmSchedulerTimeShared();

        final Host host = new HostSimple(ram, bw, storage, peList);
        host.setPowerModel(powerModel);
        host
            .setRamProvisioner(ramProvisioner)
            .setBwProvisioner(bwProvisioner)
            .setVmScheduler(vmScheduler);
        return host;
    }

}

