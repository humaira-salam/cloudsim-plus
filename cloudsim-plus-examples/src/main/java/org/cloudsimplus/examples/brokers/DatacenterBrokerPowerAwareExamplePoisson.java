/*
 * CloudSim Plus: A modern, highly-extensible and easier-to-use Framework for
 * Modeling and Simulation of Cloud Computing Infrastructures and Services.
 * http://cloudsimplus.org
 *
 *     Copyright (C) 2015-2018 Universidade da Beira Interior (UBI, Portugal) and
 *     the Instituto Federal de Educação Ciência e Tecnologia do Tocantins (IFTO, Brazil).
 *
 *     This file is part of CloudSim Plus.
 *
 *     CloudSim Plus is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     CloudSim Plus is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with CloudSim Plus. If not, see <http://www.gnu.org/licenses/>.
 */
package org.cloudsimplus.examples.brokers;

import ch.qos.logback.classic.Level;
import org.cloudbus.cloudsim.distributions.PoissonDistr;
import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicy;
import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.brokers.DatacenterBroker;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerHeuristic;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerPowerAware;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.Simulation;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.distributions.ContinuousDistribution;
import org.cloudbus.cloudsim.distributions.UniformDistr;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.power.models.*;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.ResourceProvisionerSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerSpaceShared;
import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.schedulers.vm.VmSchedulerSpaceShared;
import org.cloudbus.cloudsim.schedulers.vm.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.util.SwfWorkloadFileReader;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModel;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelFull;
import org.cloudbus.cloudsim.vms.UtilizationHistory;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.heuristics.CloudletToVmMappingHeuristic;
import org.cloudsimplus.heuristics.CloudletToVmMappingSimulatedAnnealing;
import org.cloudsimplus.heuristics.CloudletToVmMappingSolution;
import org.cloudsimplus.heuristics.HeuristicSolution;
import org.cloudsimplus.listeners.EventInfo;
import org.cloudsimplus.util.Log;
import com.opencsv.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.text.Position;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.DoubleStream;

/**
 * An example that uses a
 * <a href="http://en.wikipedia.org/wiki/Simulated_annealing">Simulated Annealing</a>
 * heuristic to find a suboptimal mapping between Cloudlets and Vm's submitted to a
 * DatacenterBroker. The number of {@link Pe}s of Vm's and Cloudlets are defined
 * randomly.
 *
 * <p>The {@link DatacenterBrokerHeuristic} is used
 * with the {@link CloudletToVmMappingSimulatedAnnealing} class
 * in order to find an acceptable solution with a high
 * {@link HeuristicSolution#getFitness() fitness value}.</p>
 *
 * <p>Different {@link CloudletToVmMappingHeuristic} implementations can be used
 * with the {@link DatacenterBrokerHeuristic} class.</p>
 *
 * <p>A comparison of cloudlet-VM mapping is done among the best fit approach,
 * heuristic approach and round robin mapping.</p>
 *
 * @author Manoel Campos da Silva Filho
 * @since CloudSim Plus 1.0
 */
public class DatacenterBrokerPowerAwareExamplePoisson {
    private static final int HOSTS_TO_CREATE = 50;
    private static final int VMS_TO_CREATE = HOSTS_TO_CREATE*3;
    private static final int CLOUDLETS_TO_CREATE = 20;
    private static final int DYNAMIC_CLOUDLETS_TO_CREATE = 20;
    private static final int HOST_PES = 8;
    private static final int VM_PES = 4;
    private static final int CLOUDLET_PES = 1;
    private double TIME_TO_CREATE_NEW_CLOUDLET = 1;
    private double MAX_TIME_FOR_CLOUDLET_ARRIVAL = 0; // -1 indicates cloudlets were created using MAX_CLOUDLETS_TO_CREATE;
    private static double MEAN_CUSTOMERS_ARRIVAL_PER_MINUTE = 0;
    private static final int MAX_CLOUDLETS_TO_CREATE = 500; //-1 here indicate that max time 'MAX_TIME_FOR_CLOUDLET_ARRIVAL' was used to create maximum cloudlets


    private static int CLOUDLET_LENGTH;
    private static boolean cloudletCreated;
    private static boolean logFileCreated;
    private double lastTime = 0;


    /**
     * Simulated Annealing (SA) parameters.
     */
    public static final double SA_INITIAL_TEMPERATURE = 1.0;
    public static final double SA_COLD_TEMPERATURE = 0.0001;
    public static final double SA_COOLING_RATE = 0.003;
    public static final int    SA_NUMBER_OF_NEIGHBORHOOD_SEARCHES = MAX_CLOUDLETS_TO_CREATE/4;

    /**
     * time to finish terminate teh simulation
     *
     */
    private double TIME_TO_FINISH_SIMULATION = 3000;

    private final Simulation simulation;
    private List<Cloudlet> cloudletList;
    private List<Vm> vmList;
    private final List<Host> hostList = new ArrayList<>();
    private List<Double> arrAllHostPow = new ArrayList<>();
    private double[] hostsTotClLen;

    /**
     * Number of cloudlets created so far.
     */
    private int createdCloudlets = 0;
    /**
     * Number of VMs created so far.
     */
    private int createdVms = 0;
    /**
     * Number of hosts created so far.
     */
    private int createdHosts = 0;
    /**
     * Broker.
     */
    private DatacenterBroker broker;

    ContinuousDistribution random;
    private static Random ran = new Random();

    /**
     * Utilization error counter
     */
    int utiErrCount = 0;

    /**
     * Defines the minimum percentage of power a Host uses,
     * even it it's idle.
     */
    private static final double STATIC_POWER_PERCENT = 0.37; //0.7

    /**
     * The max number of watt-second (Ws) of power a Host uses.
     */
    private static final int MAX_POWER_WATTS_SEC = 83; //50

    /**
     * The workload file to be read.
     */
    private static final String WORKLOAD_FILENAME = "NASA-iPSC-1993-3.1-cln.swf.gz";

    /**
     * The base dir inside the resource directory to get SWF workload files.
     */
    private static final String WORKLOAD_BASE_DIR = "workload/swf/";
    /**
     * Defines the maximum number of cloudlets to be created
     * from the given workload file.
     * The value -1 indicates that every job inside the workload file
     * will be created as one cloudlet.
     */
    private int maximumNumberOfCloudletsToCreateFromTheWorkloadFile = 500;//-1;

    private static final Logger LOGGER = LoggerFactory.getLogger(CloudSim.class.getSimpleName());

    /**
     * If set to false, consecutive lines with the the same CPU utilization and power consumption
     * will be shown only once, at the time that such metrics started to return those values.
     * The last history line is always shown, independent of any condition.
     */

    /**
     * Starts the simulation.
     * @param args
     */
    public static void main(String[] args) {

        /**
         * number of repetitions
         */
        int numRep = 5;
        final boolean verbose = true;
        final boolean showAllHostUtilizationHistoryEntries = true;
        int[] rateArr = new int[]{0, 1, 2, 4, 6, 8, 10, 12, 14, 15};
        int rateSz = 1; //rateArr.length;
        double clMax;
        logFileCreated = false;

        for(int j = 0; j < rateSz; j++) {
            clMax = rateArr[j];
            for(int i = 0; i < numRep; i++) {
                final long seed = System.currentTimeMillis();//0

                // BestFit
                cloudletCreated = false;
                final CloudSim simulation1 = new CloudSim();
                final UniformDistr random1 = new UniformDistr(0, 1, seed);
                final DatacenterBroker broker1 = new DatacenterBrokerPowerAware(simulation1);
                new DatacenterBrokerPowerAwareExamplePoisson(broker1, random1, verbose, showAllHostUtilizationHistoryEntries, i, clMax);
    //            logFileCreated = true;
    //        }

                // Heuristic
    //        logFileCreated = false;
    ////        for(int i = 1; i < numRep; i++) {
                cloudletCreated = false;
                final CloudSim simulation0 = new CloudSim();
                final UniformDistr random0 = new UniformDistr(0, 1, seed);
                final DatacenterBrokerHeuristic broker0 = createHeuristicBroker(simulation0, random0);
                new DatacenterBrokerPowerAwareExamplePoisson(broker0, random0, verbose, showAllHostUtilizationHistoryEntries, i, clMax);
    //            logFileCreated = true;
    //        }

                // Simple - RoundRobin
    //        logFileCreated = false;
    //            for(int i = 1; i < numRep; i++) {
                cloudletCreated = false;
                final CloudSim simulation2 = new CloudSim();
                final UniformDistr random2 = new UniformDistr(0, 1, seed);
                final DatacenterBroker broker2 = new DatacenterBrokerSimple(simulation2);
                new DatacenterBrokerPowerAwareExamplePoisson(broker2, random2, verbose, showAllHostUtilizationHistoryEntries, i, clMax);


//                logFileCreated = true;
             }
            logFileCreated = false;

        }
    }

    /**
     * Default constructor where the simulation is built.
     */
    public DatacenterBrokerPowerAwareExamplePoisson(final DatacenterBroker broker, final ContinuousDistribution rand,
                                                   final boolean verbose, final boolean showAllHostUtilizationHistoryEntries,
                                                   int rep, double clMax) {
        //Enables just some level of log messages.
        Log.setLevel(Level.ERROR);
        System.out.println("Starting " + getClass().getSimpleName());
        this.broker = broker;
        simulation = broker.getSimulation();
        random = rand;
        MEAN_CUSTOMERS_ARRIVAL_PER_MINUTE = clMax;
        int numOfFinishedCloudlets = 0;
//        int rep = currRep;
//        simulation.terminateAt(TIME_TO_FINISH_SIMULATION);

        final Datacenter datacenter = createDatacenter(simulation);
        cloudletList = new ArrayList<>();

        vmList = createVms(random);
//        cloudletList = createCloudlets(random);
        cloudletList = getCloudletListfromPoisson(random);


            /*Vms and cloudlets are created before the Datacenter and host
            because the example is defining the hosts based on VM requirements
            and VMs are created based on cloudlet requirements.*/
//        createCloudletsFromWorkloadFile();
        /**
         * Submit the created list of VMs and cloudlets to the broker
         */
        broker.submitVmList(vmList);
        broker.submitCloudletList(cloudletList);
        hostsTotClLen = new double[hostList.size()];

        /**
         * create log file if not created yet
         */

        if(!logFileCreated){
            createLogFile();
        }
        /**
         * on every clock tick check if the condition for generating dynamic cloudlet is satisfied
         */
//        simulation.addOnClockTickListener(this::createDynamicCloudlet);

        simulation.start();

        // print simulation results
        if (verbose) {
            final List<Cloudlet> finishedCloudlets = broker.getCloudletFinishedList();
//            finishedCloudlets.sort(Comparator.comparingLong(Cloudlet::getId));
//            new CloudletsTableBuilder(finishedCloudlets).build();
            // Now get arrival, execution and finish time for cloudlets
//            Path rootPath = Paths.get("/home/humaira/Repositories/cloudsimPlusResults/");
            try {
                String filePath = "/home/humaira/Repositories/cloudsimPlusResults/mappingComparison/cloudletsData/";
                String timeFileName = filePath + broker.getClass().getSimpleName() + "_CloudletTimeData_rep" + rep + "_arrvRate" + MEAN_CUSTOMERS_ARRIVAL_PER_MINUTE + "_totCloudlets"+ MAX_CLOUDLETS_TO_CREATE + "_arrTime" + MAX_TIME_FOR_CLOUDLET_ARRIVAL;
                // create FileWriter object with file as parameter
                FileWriter outputfile = new FileWriter(timeFileName, false);
                // create CSVWriter with ',' as separator
                CSVWriter writer = new CSVWriter(outputfile, ',',
                    CSVWriter.NO_QUOTE_CHARACTER,
                    CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                    CSVWriter.DEFAULT_LINE_END);
                // adding header to csv
                String[] header = { "Cloudlet Id", "Cloudlet Total Length", "Arrival Time", "Execution Time", "Finish Time", "Processing Time" , "Host ID", "VM Id"};
                writer.writeNext(header);
//              // create a List which contains String array
                List<String[]> data = new ArrayList<String[]>();
                for(Cloudlet cloudlet: finishedCloudlets) {
                    // add data to array list
                    data.add(new String[]{Double.toString(cloudlet.getId()), Double.toString(cloudlet.getTotalLength()),
                        Double.toString(cloudlet.getSubmissionTime()), Double.toString(cloudlet.getExecStartTime()),
                        Double.toString(cloudlet.getFinishTime()), String.valueOf(cloudlet.getWallClockTime(datacenter)),
                        String.valueOf(cloudlet.getVm().getHost().getId()), String.valueOf(cloudlet.getVm().getId())});
                    int hInd = (int)cloudlet.getVm().getHost().getId();
                    double jh = (double)cloudlet.getTotalLength();
//                    double kj;
//                    if(hostsTotClLen.size() != 0 && hostsTotClLen.size() < hInd )
//                    {
//                        double jjn = hostsTotClLen.get(hInd);
//                        kj = jh +jjn;
//                    }
//                    else {
//                        kj = jh;
//
//                    }
//
//                    hostsTotClLen.add(hInd,kj);

                    hostsTotClLen[hInd] =  hostsTotClLen[hInd] + jh;
                }
                writer.writeAll(data);
                writer.close();
                numOfFinishedCloudlets = finishedCloudlets.size();
                System.out.printf("total cloudlet finished services : %d\n", numOfFinishedCloudlets);
            }
            catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        double hostPowkWh = printHostsCpuUtilizationAndPowerConsumption(showAllHostUtilizationHistoryEntries);
        //printVmsCpuUtilizationAndPowerConsumption();
        double brokerMappingCost = print(verbose);
        datalogging(hostPowkWh, brokerMappingCost, rep, numOfFinishedCloudlets);
        System.out.printf("utilization error count : %d\n", utiErrCount);
//        System.out.printf("Below is the total cloud length on each host\n");
//        hostsTotClLen.forEach(System.out::println);


    }

    private static DatacenterBrokerHeuristic createHeuristicBroker(final CloudSim sim, final ContinuousDistribution rand) {
        CloudletToVmMappingSimulatedAnnealing heuristic = createSimulatedAnnealingHeuristic(rand);
        final DatacenterBrokerHeuristic broker = new DatacenterBrokerHeuristic(sim);
        broker.setHeuristic(heuristic);
        return broker;
    }




    private List<Cloudlet> getCloudletListfromPoisson(final ContinuousDistribution rand){
        //creates a poisson process that checks the arrival of 1 (k) cloudlet
        //1 is the default value for k
//        PoissonDistr poisson = new PoissonDistr(MEAN_CUSTOMERS_ARRIVAL_PER_MINUTE, 0);
        int totalArrivedCustomers = 0;
        int numcl = 1;
        double sampTime = 0;
        while( totalArrivedCustomers <= MAX_CLOUDLETS_TO_CREATE){
//            totalArrivedCustomers += poisson.getK();
//            sampTime = sampTime + poisson.sample();
            totalArrivedCustomers +=numcl;
            Cloudlet cloudlet = createCloudlet(getRandomPesNumber(4, rand));  //CLOUDLET_PES
            cloudlet.setSubmissionDelay(0); //(sampTime);
            cloudlet.setSubmissionTime(0); //(sampTime);
            cloudletList.add(cloudlet);

//            System.out.printf("%d cloudlets arrived at minute %f\n", poisson.getK(), sampTime);
        }
        System.out.printf("\n\t%d cloudlets have arrived in time %f\n", totalArrivedCustomers, sampTime);

//        for (int minute = 0; minute < MAX_TIME_FOR_CLOUDLET_ARRIVAL; minute++) {
////            if (poisson.eventsHappened()) { //Have k Cloudlets arrived?
//            totalArrivedCustomers += poisson.getK();
////          for (int i = 0; i < DYNAMIC_CLOUDLETS_TO_CREATE; i++) {
//            Cloudlet cloudlet = createCloudlet(getRandomPesNumber(4, rand)); //getRandomPesNumber(4, rand) //CLOUDLET_PES
//            cloudlet.setSubmissionDelay(minute);
//            cloudlet.setSubmissionTime(minute);
//            cloudletList.add(cloudlet);
////               }
//                System.out.printf("%d cloudlets arrived at minute %d\n", poisson.getK(), minute);
//            }
//        }
        System.out.printf("\n\t%d cloudlets have arrived\n", totalArrivedCustomers);
        return cloudletList;
        //
    }
    //



    private List<Cloudlet> createCloudlets(final ContinuousDistribution rand) {
        for(int i = 0; i < CLOUDLETS_TO_CREATE; i++){
            cloudletList.add(createCloudlet(getRandomPesNumber(1, rand)));
        }

        return cloudletList;
    }

    private List<Vm> createVms(final ContinuousDistribution random) {
        final List<Vm> list = new ArrayList<>(VMS_TO_CREATE);
        for(int i = 0; i < VMS_TO_CREATE; i++){
            list.add(createVm(getRandomPesNumber(4, random)));
        }

        return list;
    }

    private static CloudletToVmMappingSimulatedAnnealing createSimulatedAnnealingHeuristic(final ContinuousDistribution rand) {
        CloudletToVmMappingSimulatedAnnealing heuristic =
            new CloudletToVmMappingSimulatedAnnealing(SA_INITIAL_TEMPERATURE, rand);
        heuristic.setColdTemperature(SA_COLD_TEMPERATURE);
        heuristic.setCoolingRate(SA_COOLING_RATE);
        heuristic.setNeighborhoodSearchesByIteration(SA_NUMBER_OF_NEIGHBORHOOD_SEARCHES);
        return heuristic;
    }

    private double print(final boolean verbose) {
        final double brokersMappingCost = computeBrokersMappingCost(true);
//        final double basicRoundRobinCost = computeRoundRobinMappingCost(true);
//        System.out.printf(
//            "The solution based on %s mapper costs %.2f. Basic round robin implementation in this example costs %.2f.\n", broker.getClass().getSimpleName(), brokersMappingCost, basicRoundRobinCost);
        System.out.println(getClass().getSimpleName() + " finished!");
        return brokersMappingCost;
    }

    /**
     * Randomly gets a number of PEs (CPU cores).
     *
     * @param maxPesNumber the maximum value to get a random number of PEs
     * @return the randomly generated PEs number
     */
    private int getRandomPesNumber(final int maxPesNumber, final ContinuousDistribution random) {
        final double uniform = random.sample();
        /*always get an index between [0 and size[,
        regardless if the random number generator returns
        values between [0 and 1[ or >= 1*/
        return (int)(uniform >= 1 ? uniform % maxPesNumber : uniform * maxPesNumber) + 1;
    }

    private DatacenterSimple createDatacenter(final Simulation sim) {
//        final List<Host> hostList = new ArrayList<>();
        for(int i = 0; i < HOSTS_TO_CREATE; i++) {
            hostList.add(createHost());
        }

        VmAllocationPolicySimple vmAllocationPolicy = new VmAllocationPolicySimple();
//        vmallocationpolicy.setfindhostforvmfunction(this::findenergyeffectivehostforvm);

//        return new datacentersimple(sim, hostlist, new vmallocationpolicysimple());

        return new DatacenterSimple(simulation, hostList, vmAllocationPolicy);


    }

    /**
     * Define a specific policy to randomly select a suitable Host to place a given VM.
     * It implements a {@link Comparator} that randomly sorts the Hosts by returning a value between [-1..1]
     * (according to comparator requirements).
     * Hosts' attributes aren't even considered to ensure the randomness.
     *
     * @param vmAllocationPolicy the {@link VmAllocationPolicy} containing Host allocation information
     * @param vm the {@link Vm} to find a host to be placed
     * @return an {@link Optional} that may contain a Host in which to place a Vm, or an {@link Optional#empty()}
     *         {@link Optional} if not suitable Host was found.
     */
    private Optional<Host> findEnergyEffectiveHostForVm(VmAllocationPolicy vmAllocationPolicy, Vm vm) {
        double thr = 4000;

        Optional<Host> mhost = vmAllocationPolicy
            .getHostList()
            .stream()
            .filter(host -> (host.isSuitableForVm(vm) )) //&& (host.getAvailableMips() >= thr)
            .min(Comparator.comparing(host -> (host.getPowerModel().getPower(host.getFreePesNumber()/host.getPeList().size()) <= 75)));

        return mhost;
    }

    private Host createHost() {
        final long mips = 1000; // capacity of each CPU core (in Million Instructions per Second)
        final int  ram = 2048; // host memory (Megabyte)
        final long storage = 1000000; // host storage
        final long bw = 10000;

        final List<Pe> peList = new ArrayList<>();
        /*Creates the Host's CPU cores and defines the provisioner
        used to allocate each core for requesting VMs.*/
        for(int i = 0; i < HOST_PES; i++)
            peList.add(new PeSimple(mips, new PeProvisionerSimple()));

        final PowerModel powerModel = new PowerModelCubic(MAX_POWER_WATTS_SEC, STATIC_POWER_PERCENT);

        createdHosts++;
        return new HostSimple(ram, bw, storage, peList)
            .setRamProvisioner(new ResourceProvisionerSimple())
            .setBwProvisioner(new ResourceProvisionerSimple())
            .setVmScheduler(new VmSchedulerSpaceShared()).setPowerModel(powerModel);
    }

    private Vm createVm(final int pesNumber) {
        final long mips = 1000;
        final long   storage = 10000; // vm image size (Megabyte)
        final int    ram = 512; // vm memory (Megabyte)
        final long   bw = 1000; // vm bandwidth
        Vm vm = new VmSimple(createdVms++, mips, pesNumber) //pesNumber // VM_PES
            .setRam(ram).setBw(bw).setSize(storage)
            .setCloudletScheduler(new CloudletSchedulerTimeShared());

        vm.getUtilizationHistory().enable();
        return vm;
    }

    private Cloudlet createCloudlet(final int numberOfPes) {
        final long length = 400000; //in Million Structions (MI)
        final long fileSize = 300; //Size (in bytes) before execution
        final long outputSize = 300; //Size (in bytes) after execution
        CLOUDLET_LENGTH = ran.nextInt(1000000) + 400000; //4000; //
//
        //Defines how CPU, RAM and Bandwidth resources are used
        //Sets the same utilization model for all these resources.
        final UtilizationModel utilization = new UtilizationModelFull();

        return new CloudletSimple(createdCloudlets++, CLOUDLET_LENGTH, numberOfPes,5) // lets suppose life time is 5 sec for now, will later add random for different task using variable
            .setFileSize(fileSize)
            .setOutputSize(outputSize)
            .setUtilizationModel(utilization); //.setSubmissionTime(simulation.clock());
    }

    private void createCloudletsFromWorkloadFile() {
        final String fileName = WORKLOAD_BASE_DIR + WORKLOAD_FILENAME;
        SwfWorkloadFileReader reader = SwfWorkloadFileReader.getInstance(fileName, 1000);
        reader.setMaxLinesToRead(maximumNumberOfCloudletsToCreateFromTheWorkloadFile);
        this.cloudletList = reader.generateWorkload();
//        broker.submitCloudletList(cloudletList);
        System.out.printf("# Created %d Cloudlets for %s\n", this.cloudletList.size(), broker);
    }

    /**
     * Simulates the dynamic arrival of a Cloudlet and a VM during simulation runtime.
     * @param evt
     */
    private void createDynamicCloudlet(final EventInfo evt) {
//        System.out.printf("\n# Received OnClockTick evt-time: %.4f, sim-clock: %.4f\n", evt.getTime(), simulation.clock());
//        LOGGER.error("================= Starting CloudSim Plus at time : {}", simulation.clock());
//        System.out.println("simclock time : %d", simulation.clock());
        //
        if (!cloudletCreated && (simulation.clock() >= TIME_TO_CREATE_NEW_CLOUDLET) && (simulation.clock() <= MAX_TIME_FOR_CLOUDLET_ARRIVAL)) {
            System.out.printf("\n# Dynamically creating %d Cloudlets at event time %.2f and at simulation time %.2f\n", DYNAMIC_CLOUDLETS_TO_CREATE, evt.getTime(), simulation.clock());
            System.out.printf("\n#   Rounded event time %d and at simulation time %d\n", (int)Math.round(evt.getTime()), (int)Math.round(simulation.clock()));

            for (int i = 0; i < DYNAMIC_CLOUDLETS_TO_CREATE; i++) {
                cloudletList.add(createCloudlet(getRandomPesNumber(4, random)));
            }

            broker.submitCloudletList(cloudletList);
            lastTime = (int)Math.round(simulation.clock()) + 1;
        }
//        cloudletCreated = true;
//        System.out.printf("\n# Dynamically call at simulation time %.2f\n", simulation.clock());

    }

    private double computeRoundRobinMappingCost(boolean doPrint) {
        CloudletToVmMappingSimulatedAnnealing heuristic =
            new CloudletToVmMappingSimulatedAnnealing(SA_INITIAL_TEMPERATURE, random);
        final CloudletToVmMappingSolution roundRobinSolution = new CloudletToVmMappingSolution(heuristic);
        int i = 0;
        for (Cloudlet c : cloudletList) {
            //cyclically selects a Vm (as in a circular queue)
            roundRobinSolution.bindCloudletToVm(c, vmList.get(i));
            i = (i+1) % vmList.size();
        }

        if (doPrint) {
            printSolution(
                "Round robin solution used by DatacenterBrokerSimple class",
                roundRobinSolution, false);
        }
        return roundRobinSolution.getCost();
    }

    private double computeBrokersMappingCost(boolean doPrint) {
        CloudletToVmMappingSimulatedAnnealing heuristic =
            new CloudletToVmMappingSimulatedAnnealing(SA_INITIAL_TEMPERATURE, random);

        final CloudletToVmMappingSolution bestFitSolution = new CloudletToVmMappingSolution(heuristic);
        int numBoundCloudlets = 0;
        for (Cloudlet c : cloudletList) {
            if (c.isBoundToVm()) {
                bestFitSolution.bindCloudletToVm(c, c.getVm());
                numBoundCloudlets++;
            }
        }

        if (doPrint) {
            System.out.println("Solution based on mapper: " + broker.getClass().getSimpleName());
            printSolution(
                "Solution cost and fitness of running broker in DatacenterBrokeris:",
                bestFitSolution, false);
            System.out.printf("Total cloudlets bounded to VMs %d\n", numBoundCloudlets);
        }

        return bestFitSolution.getCost();
    }


    private void printSolution(
        final String title,
        final CloudletToVmMappingSolution solution,
        final boolean showIndividualCloudletFitness)
    {
        System.out.printf("%s (cost %.2f fitness %.6f)\n",
            title, solution.getCost(), solution.getFitness());
        if(!showIndividualCloudletFitness)
            return;
        for(Map.Entry<Cloudlet, Vm> e: solution.getResult().entrySet()){
            System.out.printf(
                "Cloudlet %3d (%d PEs, %6d MI) mapped to Vm %3d (%d PEs, %6.0f MIPS)\n",
                e.getKey().getId(),
                e.getKey().getNumberOfPes(), e.getKey().getLength(),
                e.getValue().getId(),
                e.getValue().getNumberOfPes(), e.getValue().getMips());
        }
        System.out.println();
    }

    /**
     * The Host CPU Utilization History is only computed
     * if VMs utilization history is enabled by calling
     * {@code vm.getUtilizationHistory().enable()}.
     */
    private double printHostsCpuUtilizationAndPowerConsumption(final boolean showAllHostUtilizationHistoryEntries) {
        System.out.println();
        for (final Host host : hostList) {
            double hostPow = printHostCpuUtilizationAndPowerConsumption(host, showAllHostUtilizationHistoryEntries);
            arrAllHostPow.add(hostPow);
        }
        double hostkWh = arrAllHostPow.stream().mapToDouble(a->a).sum();
        System.out.printf("Total Energy cost : %.5f KWatt-Hour \n", hostkWh);
        return hostkWh;
    }

    private double printHostCpuUtilizationAndPowerConsumption(final Host host,final boolean showAllHostUtilizationHistoryEntries) {
//        System.out.printf("Host %d CPU utilization and power consumption\n", host.getId());
//        System.out.println("----------------------------------------------------------------------------------------------------------------------");
        final Map<Double, DoubleSummaryStatistics> utilizationPercentHistory = host.getUtilizationHistory();
        double totalWattsSec = 0;
        double prevUtilizationPercent = -1, prevWattsSec = -1;
        //time difference from the current to the previous line in the history
        double utilizationHistoryTimeInterval;
        double prevTime=0;
        for (Map.Entry<Double, DoubleSummaryStatistics> entry : utilizationPercentHistory.entrySet()) {
            utilizationHistoryTimeInterval = entry.getKey() - prevTime;
            //The total Host's CPU utilization for the time specified by the map key
            double utilizationPercent = entry.getValue().getSum();
            if(utilizationPercent > 1 || utilizationPercent < 0){
                System.out.printf("some utilization error with utilization value :%.15f \n", utilizationPercent);
                utiErrCount = utiErrCount+1;
                if(utilizationPercent > 1){
                    utilizationPercent = 1;
                }
            }

            //final double watts = host.getPowerModel().getExpPowerOfHost(Vm);

            final double watts = host.getPowerModel().getPower(utilizationPercent);
            //Energy consumption in the time interval
            final double wattsSec = watts*utilizationHistoryTimeInterval;
            //Energy consumption in the entire simulation time
            totalWattsSec += wattsSec;
            //only prints when the next utilization is different from the previous one, or it's the first one
            if(showAllHostUtilizationHistoryEntries || prevUtilizationPercent != utilizationPercent || prevWattsSec != wattsSec) {
//                System.out.printf(
//                    "\tTime %8.1f | Host CPU Usage: %6.1f%% | Power Consumption: %8.0f Watts * %6.0f Secs = %10.2f Watt-Sec\n",
//                    entry.getKey(), utilizationPercent * 100, watts, utilizationHistoryTimeInterval, wattsSec);
            }
            prevUtilizationPercent = utilizationPercent;
            prevWattsSec = wattsSec;
            prevTime = entry.getKey();
        }
//        System.out.printf(
//            "Total Host %d Power Consumption in %.0f secs: %.0f Watt-Sec (%.5f KWatt-Hour)\n",
//            host.getId(), simulation.clock(), totalWattsSec, PowerAware.wattsSecToKWattsHour(totalWattsSec));
        final double powerWattsSecMean = totalWattsSec / simulation.clock();
        double watPow = PowerAware.wattsSecToKWattsHour(powerWattsSecMean); // why is not correct
//
//        System.out.printf(
//            "total power is %.2f in total time %.3f, Mean %.2f Watt-Sec for %d usage samples (%.5f KWatt-Hour)\n", totalWattsSec, simulation.clock(),
//            powerWattsSecMean, utilizationPercentHistory.size(), PowerAware.wattsSecToKWattsHour(powerWattsSecMean));
//        System.out.println("----------------------------------------------------------------------------------------------------------------------\n");
        return  PowerAware.wattsSecToKWattsHour(totalWattsSec); //powerWattsSecMean;
    }

    private void printVmsCpuUtilizationAndPowerConsumption() {
        for (Vm vm : vmList) {
            System.out.println("Vm " + vm.getId() + " at Host " + vm.getHost().getId() + " CPU Usage and Power Consumption");
            System.out.println("----------------------------------------------------------------------------------------------------------------------");
            double vmPower; //watt-sec
            double utilizationHistoryTimeInterval, prevTime = 0;
            final UtilizationHistory history = vm.getUtilizationHistory();
            for (Map.Entry<Double, Double> entry : history.getHistory().entrySet()) {
//                for (final double time : history.getHistory().keySet()) {
                double time = entry.getKey();
                utilizationHistoryTimeInterval = time - prevTime;
                vmPower = history.powerConsumption(time);
                final double wattsPerInterval = vmPower * utilizationHistoryTimeInterval;
                double vmUtilInHost = history.cpuUsageFromHostCapacity(time);
                System.out.printf(
                    "\tTime %8.1f | Host CPU Usage: %6.1f%% (%6.1f MIPs) | VM CPU Usage: %6.1f%% (%6.1f MIPs) | " +
                        "Power Consumption: %8.0f Watt-Sec * %6.0f Secs = %10.2f Watt-Sec\n",
                    time, vmUtilInHost * 100, vmUtilInHost*vm.getHost().getTotalMipsCapacity(),
                    entry.getValue() * 100, entry.getValue() * vm.getTotalMipsCapacity(),vmPower,
                    utilizationHistoryTimeInterval, wattsPerInterval);
                prevTime = time;
            }
            System.out.println();
        }
    }

    private void createLogFile() {
        String filePath = "/home/humaira/Repositories/cloudsimPlusResults/mappingComparison/";
        try {
            String filename = broker.getClass().getSimpleName();
            // create FileWriter object with file as parameter
            String logFileName = filePath + filename + "_SimulationResults" + "_arrvRate" + MEAN_CUSTOMERS_ARRIVAL_PER_MINUTE + "_totCloudlets"+ MAX_CLOUDLETS_TO_CREATE + "_arrTime" + MAX_TIME_FOR_CLOUDLET_ARRIVAL;
            FileWriter outputfile = new FileWriter(logFileName, true);

            // create CSVWriter with '|' as separator
            CSVWriter writer = new CSVWriter(outputfile, ',',
                CSVWriter.NO_QUOTE_CHARACTER,
                CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                CSVWriter.DEFAULT_LINE_END);

            // adding header to csv
            String[] header = { "No. of Repetition", "Broker Name", "Simulation Finish Time", "Total No. of Hosts",
                "No. of Idle Hosts", "Sum of Cloudlet Lengths on Hosts", "No. of VMs", "No. of Cloudets Submitted",
                "No. Cloudlets Finished", "Power Consumed(kWh)", "Cost" };
            writer.writeNext(header);

            writer.close();
        }
        catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void datalogging(double hostPowkWh, double brokersMappingCost, int rep, int finishedCLoudlets) {
        String filePath = "/home/humaira/Repositories/cloudsimPlusResults/mappingComparison/";
        try {
            String filename = broker.getClass().getSimpleName();
            // create FileWriter object with file as parameter
            String logFileName = filePath + filename + "_SimulationResults" + "_arrvRate" + MEAN_CUSTOMERS_ARRIVAL_PER_MINUTE + "_totCloudlets"+ MAX_CLOUDLETS_TO_CREATE + "_arrTime" + MAX_TIME_FOR_CLOUDLET_ARRIVAL;
            // create FileWriter object with file as parameter
            FileWriter outputfile = new FileWriter(logFileName, true);
            // create CSVWriter with '|' as separator
            CSVWriter writer = new CSVWriter(outputfile, ',',
                CSVWriter.NO_QUOTE_CHARACTER,
                CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                CSVWriter.DEFAULT_LINE_END);


            // get total INS(cloudlet length) on each host
            double hostTotIns = DoubleStream.of(hostsTotClLen).sum();


            //get number of idle hosts
            double idleHost = arrAllHostPow.stream().filter(x -> x == 0).count();

            //write data to csv
            String[] data = { Integer.toString(rep), broker.getClass().getSimpleName(),
                String.valueOf(simulation.getSimFinishTime()), Double.toString(arrAllHostPow.size()),
                Double.toString(idleHost), String.valueOf(hostTotIns), String.valueOf(vmList.size()), String.valueOf(cloudletList.size()),
                String.valueOf(finishedCLoudlets), String.valueOf(hostPowkWh), String.valueOf(brokersMappingCost)};
            writer.writeNext(data);
            writer.close();
        }
        catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
//
}

