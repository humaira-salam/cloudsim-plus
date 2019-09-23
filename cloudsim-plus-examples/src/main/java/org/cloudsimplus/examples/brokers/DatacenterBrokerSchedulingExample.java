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
import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.schedulers.vm.VmSchedulerSpaceShared;
import org.cloudbus.cloudsim.util.SwfWorkloadFileReader;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModel;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelFull;
import org.cloudbus.cloudsim.vms.UtilizationHistory;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;
import org.cloudsimplus.heuristics.CloudletToVmMappingHeuristic;
import org.cloudsimplus.heuristics.CloudletToVmMappingSimulatedAnnealing;
import org.cloudsimplus.heuristics.CloudletToVmMappingSolution;
import org.cloudsimplus.heuristics.HeuristicSolution;
import org.cloudsimplus.listeners.EventInfo;
import org.cloudsimplus.util.Log;
import com.opencsv.*;
import org.openjdk.jmh.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import static org.cloudbus.cloudsim.power.models.PowerAware.wattsSecToKWattsHour;

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
 * @author Manoel Campos da Silva Filh
 * @since CloudSim Plus 1.0
 */
public class DatacenterBrokerSchedulingExample {
    private static final int HOSTS_TO_CREATE = 5;
    private static final int VMS_TO_CREATE = 20;//HOSTS_TO_CREATE * 3;
    private static final int CLOUDLETS_TO_CREATE = 50; // for creating cloudlets at one tijme instant
    private static int[] DYNAMIC_CLOUDLETS_TO_CREATE;
    private static int dynAndstaCloudlets;
    private static final int HOST_PES = 8;
    private static final int VM_PES = 4;
    private static final int CLOUDLET_PES = 1;
    private double TIME_TO_CREATE_NEW_CLOUDLET = 0;
    private double MAX_TIME_FOR_CLOUDLET_ARRIVAL = 0; //1000; // -1 indicates cloudlets were created using MAX_CLOUDLETS_TO_CREATE;
    private static double MEAN_CUSTOMERS_ARRIVAL_PER_MINUTE = 1;//2;
    double subTime = 0;
    int numOfFinishedCloudlets;


    private static int CLOUDLET_LENGTH;

    // flags to create new files and folder
    private static boolean cloudletCreated;
    private static boolean logFileCreated;
    private static boolean logFilefolderCreated = false;


    /**
     * Simulated Annealing (SA) parameters.
     */
    public static final double SA_INITIAL_TEMPERATURE = 1.0;
    public static final double SA_COLD_TEMPERATURE = 0.0001;
    public static final double SA_COOLING_RATE = 0.003;
    public static final int SA_NUMBER_OF_NEIGHBORHOOD_SEARCHES = dynAndstaCloudlets;

    /**
     * time to finish terminate the simulation
     */
    private double TIME_TO_FINISH_SIMULATION = 3000;

    private final Simulation simulation;

    //initialize arrays and list to store data
    private List<Cloudlet> cloudletList;
    private List<Cloudlet> dynamicClgen;
    private List<Vm> vmList;
    private final List<Host> hostList = new ArrayList<>();
    private List<Double> arrAllHostPow = new ArrayList<>();
    private List<Double> arrAllHostTime = new ArrayList<>();
    private List<Double> arrAllHostUti = new ArrayList<>();
    private double[] hostDataStore = new double[3];
    private double[] costArr = new double[2];
    private double[] hostsTotClLen;
    private String filePathSim;
    private String filePathCl;
    private int batchCl = 0;
    static int dCl;

    // set true if want to displaz or print results
    final boolean verbose = true;
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

    /**
     * create continous random distribution
     */
    ContinuousDistribution random;
    private static Random ran = new Random();

    /**
     * Initiate parameters for poisson distribution
     */
    double[] poissonTime; // array of time stamp to create task according to Poisson
    int poisInd = 0;
    double lastTime = 0;
    int cntTotdygen = 0; // total dynmaic cloudlet generated
    /**
     * Utilization error counter
     */
    int utiErrCount = 0; // in some cases utilization goes greater than 1 and value is always to 1.000000002 // need to find out its cause

    /**
     * Defines the minimum percentage of power a Host uses,
     * even it it's idle.
     */
    private static final double STATIC_POWER_PERCENT = 0.37; //0.7

    /**
     * The max number of watt-second (Ws) of power a Host uses.
     */
    private static final int MAX_POWER_WATTS_SEC = 83; //50


    private static final Logger LOGGER = LoggerFactory.getLogger(CloudSim.class.getSimpleName());
    private double[] genEvtTime;


    /**
     * If set to false, consecutive lines with the the same CPU utilization and power consumption
     * will be shown only once, at the time that such metrics started to return those values.
     * The last history line is always shown, independent of any condition.
     */

    /**
     * Starts the simulation.
     *
     * @param args
     */
    public static void main(String[] args) {

        /**
         * number of repetitions
         */
        int numRep = 1;
        /**
         * set true if wants to print the  results
         */

        final boolean showAllHostUtilizationHistoryEntries = true;
        int[] rateArr = new int[]{10}; //, 2, 4, 6, 8, 10, 12, 14, 16};
        double clMax;
        DYNAMIC_CLOUDLETS_TO_CREATE = new int[]{10};//0, 50, 100, 150, 200, 250, 300, 350, 400, 450};
        logFileCreated = false;
//        logFilefolderCreated = false;
        // loop to get the total number of dynamic cloudlets to be created
        for (int d = 0; d < DYNAMIC_CLOUDLETS_TO_CREATE.length; d++) {
            dCl = DYNAMIC_CLOUDLETS_TO_CREATE[d];
            // loop to get the current poisson arrival rate to be used in simulations
            for (int j = 0; j < rateArr.length; j++) {
                clMax = rateArr[j];
                // loop for the number of repetitions
                for (int i = 0; i < numRep; i++) {
                    final long seed = System.currentTimeMillis();//0

                    // BestFit
                    cloudletCreated = false;
                    final CloudSim simulation1 = new CloudSim();
                    final UniformDistr random1 = new UniformDistr(0, 1, seed);
                    final DatacenterBroker broker1 = new DatacenterBrokerPowerAware(simulation1);
                    new DatacenterBrokerSchedulingExample(broker1, random1, showAllHostUtilizationHistoryEntries, i, clMax, dCl);

                    // Heuristic
                    cloudletCreated = false;
                    final CloudSim simulation0 = new CloudSim();
                    final UniformDistr random0 = new UniformDistr(0, 1, seed);
                    final DatacenterBrokerHeuristic broker0 = createHeuristicBroker(simulation0, random0);
                    new DatacenterBrokerSchedulingExample(broker0, random0, showAllHostUtilizationHistoryEntries, i, clMax, dCl);

//                  Simple - RoundRobin
                    cloudletCreated = false;
                    final CloudSim simulation2 = new CloudSim();
                    final UniformDistr random2 = new UniformDistr(0, 1, seed);
                    final DatacenterBroker broker2 = new DatacenterBrokerSimple(simulation2);
                    new DatacenterBrokerSchedulingExample(broker2, random2, showAllHostUtilizationHistoryEntries, i, clMax, dCl);

                    logFileCreated = true;
                }
//            logFileCreated = false;
            }
        }
    }

    /**
     * Default constructor where the simulation is built.
     */
    public DatacenterBrokerSchedulingExample(final DatacenterBroker broker, final ContinuousDistribution rand,
                                             final boolean showAllHostUtilizationHistoryEntries,
                                             int numRep, double clMax, int dCl) {
        //Enables just some level of log messages.
        Log.setLevel(Level.ERROR);
        System.out.println("Starting " + getClass().getSimpleName());
        this.broker = broker;
        simulation = broker.getSimulation();
        //get continous distribution
        random = rand;
        //get cloudlets arrival rate
        MEAN_CUSTOMERS_ARRIVAL_PER_MINUTE = clMax;
        numOfFinishedCloudlets = 0;
//        simulation.terminateAt(TIME_TO_FINISH_SIMULATION); // used for the case where simulation is terminated by given times
        final Datacenter datacenter = createDatacenter(simulation);
        //intialize different arrays
        cloudletList = new ArrayList<>();
        dynamicClgen = new ArrayList<>();
        vmList = createVms(random);
        cloudletList = createCloudlets(random);
        poissonTime = getTimefromPoisson(random);
        hostsTotClLen = new double[hostList.size()];
        dynAndstaCloudlets = CLOUDLETS_TO_CREATE + dCl; //total cloudlets to be created including static at start and all dynamic coming

        /**Vms and cloudlets are created before the Datacenter and host
         because the example is defining the hosts based on VM requirements
         and VMs are created based on cloudlet requirements.
         */

        /**
         * Submit the created list of VMs and cloudlets to the broker
         */
        broker.submitVmList(vmList);
        //apply LRPT rule for proposed(best fit)scheme here, otherwise submit as they arrive for other schemes
        if (broker.getClass().getSimpleName() == "DatacenterBrokerPowerAware") {
            /**different cloudlets arranging rule considering the objective function
             * It could be LPT, SPT, or duetime+processing time LPT or SPT etc.
             * Objective function here could be lateness, overall completion time of batch
             */
            List<Cloudlet> latenessArrCloudlet = cloudletList.stream().sorted(Comparator.comparingDouble(x -> (
                ((x.getLength()) / vmList.get(0).getMips())))).sorted(Comparator.reverseOrder()).collect(Collectors.toList()); //
            broker.submitCloudletList(latenessArrCloudlet);
        } else {
            broker.submitCloudletList(cloudletList);
        }
        /**
         * Create Folders
         */
        if (!logFilefolderCreated) {
            createFolders();
            logFilefolderCreated = true;
        }
        /**
         * create log file if not created yet
         */
//        if(!logFileCreated){
//            createLogFile();
//        }
        /**
         * Call dynamic cloudlet create function at every simulation clock tick
         * on every clock tick check if the condition for generating dynamic cloudlet is satisfied
         */
        simulation.addOnClockTickListener(this::createDynamicCloudlet);
        simulation.start(); // start the simulation

        // get all simulation results

        double[] hostPowkWh = printHostsCpuUtilizationAndPowerConsumption(showAllHostUtilizationHistoryEntries);
//        printVmsCpuUtilizationAndPowerConsumption();
        double[] brokerMappingCost = print();
        //log all data of system and cloudlets
        datalogging(hostPowkWh, brokerMappingCost, numRep, numOfFinishedCloudlets);
        logCloudletData(broker, numRep);
        if(verbose) {
            System.out.printf("utilization error count : %d\n", utiErrCount);
            System.out.printf("Below is the total cloud length on each host\n");
//            hostsTotClLen.forEach(System.out::println);
        }
    }

    /**
     *..........***********.......... Now below here are all the functions called in main above..........**************.................
     */

    /**
     * Creating heuristic datacenter
     * @param sim
     * @param rand
     * @return
     */
    private static DatacenterBrokerHeuristic createHeuristicBroker(final CloudSim sim, final ContinuousDistribution rand) {
        CloudletToVmMappingSimulatedAnnealing heuristic = createSimulatedAnnealingHeuristic(rand);
        final DatacenterBrokerHeuristic broker = new DatacenterBrokerHeuristic(sim);
        broker.setHeuristic(heuristic);
        return broker;
    }

    /**
     * Get the time instance accoring to poisson distribution to generate cloudlets at those time
     * @param rand
     * @return
     */
    private double[] getTimefromPoisson(final ContinuousDistribution rand) {

        PoissonDistr poisson = new PoissonDistr(MEAN_CUSTOMERS_ARRIVAL_PER_MINUTE, 0);
        int totalArrivedCustomers = 0;
        poissonTime = new double[dCl]; //sCl is the dynamic cloudlets to create
//        poissonTimeS = 5; //start dynamic cloudlets creation after 5 seconds of simulation start
        while (totalArrivedCustomers < dCl) {
            if (totalArrivedCustomers > 0) {
                poissonTime[totalArrivedCustomers] = poissonTime[totalArrivedCustomers - 1] + poisson.sample();
            } else {
                poissonTime[totalArrivedCustomers] = 5 + poisson.sample();

            }
            totalArrivedCustomers += 1;
        }
        return poissonTime;
    }

    /**
     * Create Datacenter
     * @param sim
     * @return
     */
    private Datacenter createDatacenter(final Simulation sim) {
        for (int i = 0; i < HOSTS_TO_CREATE; i++) {
            hostList.add(createHost());
        }
        VmAllocationPolicySimple vmAllocationPolicy = new VmAllocationPolicySimple();
        double schedulingInterval = 0;
        Datacenter dc = new DatacenterSimple(simulation, hostList, vmAllocationPolicy);
        dc.setSchedulingInterval(schedulingInterval);
        return dc;
    }

    /**
     * Create Host
     * @return
     */
    private Host createHost() {
        final long mips = 1000; // capacity of each CPU core (in Million Instructions per Second)
        final int ram = 2048; // host memory (Megabyte)
        final long storage = 1000000; // host storage
        final long bw = 10000;

        final List<Pe> peList = new ArrayList<>();
        /*Creates the Host's CPU cores and defines the provisioner
        used to allocate each core for requesting VMs.*/
        for (int i = 0; i < HOST_PES; i++)
            peList.add(new PeSimple(mips, new PeProvisionerSimple()));
        //power model for host
        final PowerModel powerModel = new PowerModelLinear(MAX_POWER_WATTS_SEC, STATIC_POWER_PERCENT);
        createdHosts++;
        return new HostSimple(ram, bw, storage, peList)
            .setBwProvisioner(new ResourceProvisionerSimple())
            .setVmScheduler(new VmSchedulerSpaceShared()).setPowerModel(powerModel);
    }

    /**
     * Create VMs
     * @param random
     * @return
     */
    private List<Vm> createVms(final ContinuousDistribution random) {
        final List<Vm> list = new ArrayList<>(VMS_TO_CREATE);
        for (int i = 0; i < VMS_TO_CREATE; i++) {
            list.add(createVm(getRandomPesNumber(4, random)));
        }
        return list;
    }

    /**
     * Create cloudlets
     * @param rand
     * @return
     */
    private List<Cloudlet> createCloudlets(final ContinuousDistribution rand) {
        for (int i = 0; i < CLOUDLETS_TO_CREATE; i++) {
            Cloudlet cloudlet = createCloudlet(getRandomPesNumber(4, rand));  //CLOUDLET_PES
            cloudletList.add(cloudlet);
        }
        return cloudletList;
    }

    /**
     * Create VM function
     * @param pesNumber
     * @return
     */
    private Vm createVm(final int pesNumber) {
        final long mips = 1000;
        final long storage = 10000; // vm image size (Megabyte)
        final int ram = 512; // vm memory (Megabyte)
        final long bw = 1000; // vm bandwidth
        Vm vm = new VmSimple(createdVms++, mips, pesNumber) //pesNumber // VM_PES
            .setRam(ram).setBw(bw).setSize(storage)
            .setCloudletScheduler(new CloudletSchedulerTimeShared());
        vm.getUtilizationHistory().enable();
        return vm;
    }

    /**
     * Create Cloudlet function
     * @param numberOfPes
     * @return
     */

    private Cloudlet createCloudlet(final int numberOfPes) {
        //Defines how CPU, RAM and Bandwidth resources are used
        final long length = 400000; //in Million Structions (MI)
        final long fileSize = 300; //Size (in bytes) before execution
        final long outputSize = 300; //Size (in bytes) after execution
        CLOUDLET_LENGTH = ran.nextInt(10000) + 1000; //4000; //
        final double randDueTime = (CLOUDLET_LENGTH / 1000) + 5; // cloudlet length divided by average VM MIPs capacity
        //Sets the same utilization model for all these resources.
        final UtilizationModel utilization = new UtilizationModelFull();
        return new CloudletSimple(createdCloudlets++, CLOUDLET_LENGTH, numberOfPes) // lets suppose life time is 5 sec for now, will later add random for different task using variable
            .setFileSize(fileSize).setOutputSize(outputSize)
            .setUtilizationModel(utilization).setSubmissionTime(subTime)
            .setDueTime(subTime + randDueTime).setLifeTime(subTime + randDueTime + 10);//.setSubmissionTime(simulation.clock()); // give leverage of 12 seconds it must be finished or allocated with in 10 sec of its arrival
        // and if task take 5 extra sec to finish it is ok, so it must be assigned as soon possibles
    }
    /**
     * Randomly gets a number of PEs (CPU cores). for VMs and cloudlets whoever have random PEs assinged
     *
     * @param maxPesNumber the maximum value to get a random number of PEs
     * @return the randomly generated PEs number
     */
    private int getRandomPesNumber(final int maxPesNumber, final ContinuousDistribution random) {
        final double uniform = random.sample();
        /*always get an index between [0 and size[,
        regardless if the random number generator returns
        values between [0 and 1[ or >= 1*/
        return (int) (uniform >= 1 ? uniform % maxPesNumber : uniform * maxPesNumber) + 1;
    }


    /**
     * Create SA Heuristic Mapping solution
     * @param rand
     * @return
     */
    private static CloudletToVmMappingSimulatedAnnealing createSimulatedAnnealingHeuristic(final ContinuousDistribution rand) {
        CloudletToVmMappingSimulatedAnnealing heuristic =
            new CloudletToVmMappingSimulatedAnnealing(SA_INITIAL_TEMPERATURE, rand);
        heuristic.setColdTemperature(SA_COLD_TEMPERATURE);
        heuristic.setCoolingRate(SA_COOLING_RATE);
        heuristic.setNeighborhoodSearchesByIteration(SA_NUMBER_OF_NEIGHBORHOOD_SEARCHES);
        double gg = heuristic.getHeuSolFindingTime();
        return heuristic;
    }


    /**
     * Simulates the dynamic arrival of a Cloudlet during simulation runtime.
     *
     * @param evt
     */
    private void createDynamicCloudlet(final EventInfo evt) {
//        System.out.printf("\n# Received OnClockTick evt-time: %.4f, sim-clock: %.4f\n", evt.getTime(), simulation.clock());
        if((cloudletList.size() < dynAndstaCloudlets)) {
            double timeInd = poissonTime[poisInd];
            if (!cloudletCreated && (simulation.clock() >= timeInd)) {
                int cltoCreate = (int) Arrays.stream(poissonTime).filter(x -> (x < simulation.clock() && x > lastTime)).count(); // get cloudlets to cretae in time interval
                for (int i = 0; i < cltoCreate; i++) {
                    subTime = simulation.clock();
                    Cloudlet cloudlet = createCloudlet(getRandomPesNumber(4, random));  //CLOUDLET_PES
                    cloudletList.add(cloudlet);
                    dynamicClgen.add(cloudlet);
                    cntTotdygen += 1;
                    lastTime = poissonTime[poisInd];
                    poisInd += 1;
                }
                if (cltoCreate > 0) {
                    // System.out.printf("\n# Dynamically creating %d Cloudlets at event time %.2f and at simulation time %.2f\n", i, evt.getTime(), simulation.clock());
                    // System.out.printf("\n#   Rounded event time %.4f and at simulation time %.4f\n", evt.getTime(), simulation.clock());
                }
                batchCl += cltoCreate;
                if (batchCl >= 50) {
                    if (broker.getClass().getSimpleName() == "DatacenterBrokerPowerAware") {
                        List<Cloudlet> latenessArrCloudlet = dynamicClgen.stream().sorted(Comparator.comparingDouble(x -> (x.getDueTime() + ((x.getLength()) / vmList.get(0).getMips())))).sorted(Comparator.reverseOrder()).collect(Collectors.toList()); //.sorted(Comparator.reverseOrder())
                        broker.submitCloudletList(latenessArrCloudlet);
                    } else {
                        broker.submitCloudletList(dynamicClgen);
                    }
                    batchCl = 0;
                    dynamicClgen.removeAll(dynamicClgen);
                }
            }
        }

    }

    /**
     * Print results
     * @return
     */
    private double[] print() {
        final double[] brokersMappingCost = computeBrokersMappingCost();
//        final double basicRoundRobinCost = computeRoundRobinMappingCost(true);
//        System.out.printf(
//            "The solution based on %s mapper costs %.2f. Basic round robin implementation in this example costs %.2f.\n", broker.getClass().getSimpleName(), brokersMappingCost, basicRoundRobinCost);
        if(verbose) {
            System.out.println(getClass().getSimpleName() + " finished!");
        }
        return brokersMappingCost;
    }

    /**
     * Compute th cost of system
     * @return
     */
    private double[] computeBrokersMappingCost() {
        // this heuristic object here is just a dummy for bestfit and roundrobin, as we just wanted to get the VMtocloudlets map as a 'solutions' to get its cost
        CloudletToVmMappingSimulatedAnnealing heuristic =
            new CloudletToVmMappingSimulatedAnnealing(SA_INITIAL_TEMPERATURE, random);
        long heuSolTime = (long) heuristic.getHeuSolFindingTime();

        final CloudletToVmMappingSolution bestFitSolution = new CloudletToVmMappingSolution(heuristic);
        int numBoundCloudlets = 0;
        for (Cloudlet c : cloudletList) {
            if (c.isBoundToVm()) {
                bestFitSolution.bindCloudletToVm(c, c.getVm());
                numBoundCloudlets++;
            }
        }
        // although this cost is not correct..It is actually the number of cores and processing time in total...although it must be just for excessive core...some issue
        costArr[0] = bestFitSolution.getCost();
        costArr[1] = heuSolTime;
        if (verbose) {
            System.out.println("Solution based on mapper: " + broker.getClass().getSimpleName());
            printSolution("Solution cost and fitness of running broker in DatacenterBrokeris:", bestFitSolution, false);
            System.out.printf("Total cloudlets bounded to VMs %d\n", numBoundCloudlets);
            System.out.printf("Total cost(processing time) on all VMs is: %.10f, Total Time taken by Heuristic to find solution is: %.10f\n", costArr[0], costArr[1]);
        }
        return costArr;
    }

    /**
     * Print results
     * @param title
     * @param solution
     * @param showIndividualCloudletFitness
     */

    private void printSolution(final String title, final CloudletToVmMappingSolution solution, final boolean showIndividualCloudletFitness) {
        System.out.printf("%s (cost %.2f fitness %.6f)\n", title, solution.getCost(), solution.getFitness());
        if (!showIndividualCloudletFitness)
            return;
        for (Map.Entry<Cloudlet, Vm> e : solution.getResult().entrySet()) {
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
    private double[] printHostsCpuUtilizationAndPowerConsumption(final boolean showAllHostUtilizationHistoryEntries) {
        System.out.println();
        for (final Host host : hostList) {
            double[] hostData = printHostCpuUtilizationAndPowerConsumption(host, showAllHostUtilizationHistoryEntries);
            arrAllHostUti.add(hostData[0]);
            arrAllHostTime.add(hostData[1]);
            arrAllHostPow.add(hostData[2]);
        }
        double hostUti = arrAllHostUti.stream().mapToDouble(a -> a).sum();
        double hostTime = arrAllHostTime.stream().mapToDouble(a -> a).sum();
        double hostkWh = arrAllHostPow.stream().mapToDouble(a -> a).sum();
        if(verbose) {
            System.out.printf("Total CPU utilization: %.5f percentage for total hosts: %d \n", hostUti, hostList.size());
            System.out.printf("Total Total Host active time : %.5f sec for total hosts: %d\n", hostTime, hostList.size());
            System.out.printf("Total Energy cost : %.5f KWatt-Hour for total hosts: %d\n", hostkWh, hostList.size());
            System.out.printf("Total Cost : %.5f  for total hosts: %d\n", hostUti * hostTime, hostList.size());
        }
        hostDataStore[0] = hostUti;
        hostDataStore[1] = hostTime;
        hostDataStore[2] = hostkWh;

        return hostDataStore;
    }

    private double[] printHostCpuUtilizationAndPowerConsumption(final Host host, final boolean showAllHostUtilizationHistoryEntries) {
//        System.out.printf("Host %d CPU utilization and power consumption\n", host.getId());
//        System.out.println("----------------------------------------------------------------------------------------------------------------------");
        final Map<Double, DoubleSummaryStatistics> utilizationPercentHistory = host.getUtilizationHistory();
        double hostUtilTot = 0;
        double hostUtilTime = 0;
        double totalWattsSec = 0;
        double prevUtilizationPercent = -1, prevWattsSec = -1;
        double[] hostUtiData = new double[3];
        //time difference from the current to the previous line in the history
        double utilizationHistoryTimeInterval;
        double prevTime = 0;
        for (Map.Entry<Double, DoubleSummaryStatistics> entry : utilizationPercentHistory.entrySet()) {
            utilizationHistoryTimeInterval = entry.getKey() - prevTime;
            //The total Host's CPU utilization for the time specified by the map key
            double utilizationPercent = entry.getValue().getSum();
            if (utilizationPercent > 1 || utilizationPercent < 0) {
                System.out.printf("some utilization error with utilization value :%.15f \n", utilizationPercent);
                utiErrCount = utiErrCount + 1;
                if (utilizationPercent > 1) {
                    utilizationPercent = 1;
                }
            }
            //
            hostUtilTot += utilizationPercent;
            hostUtilTime += utilizationHistoryTimeInterval;
            //final double watts = host.getPowerModel().getExpPowerOfHost(Vm);

            final double watts = host.getPowerModel().getPower(utilizationPercent);
            //Energy consumption in the time interval
            final double wattsSec = watts * utilizationHistoryTimeInterval;
            //Energy consumption in the entire simulation time
            totalWattsSec += wattsSec;
            //only prints when the next utilization is different from the previous one, or it's the first one
            if (showAllHostUtilizationHistoryEntries || prevUtilizationPercent != utilizationPercent || prevWattsSec != wattsSec) {
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
        double watPow = wattsSecToKWattsHour(powerWattsSecMean); // why is not correct
//
//        System.out.printf(
//            "total power is %.2f in total time %.3f, Mean %.2f Watt-Sec for %d usage samples (%.5f KWatt-Hour)\n", totalWattsSec, simulation.clock(),
//            powerWattsSecMean, utilizationPercentHistory.size(), PowerAware.wattsSecToKWattsHour(powerWattsSecMean));
//        System.out.println("----------------------------------------------------------------------------------------------------------------------\n");
        //[hostUtilTot hostUtilTime PowerAware.wattsSecToKWattsHour(totalWattsSec)];
        hostUtiData[0] = hostUtilTot;
        hostUtiData[1] = hostUtilTime;
        hostUtiData[2] = PowerAware.wattsSecToKWattsHour(totalWattsSec);
        return hostUtiData; //PowerAware.wattsSecToKWattsHour(totalWattsSec); //powerWattsSecMean;
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
                    time, vmUtilInHost * 100, vmUtilInHost * vm.getHost().getTotalMipsCapacity(),
                    entry.getValue() * 100, entry.getValue() * vm.getTotalMipsCapacity(), vmPower,
                    utilizationHistoryTimeInterval, wattsPerInterval);
                prevTime = time;
            }
            System.out.println();
        }
    }

    /**
     * Create folders to store data
     */
    private void createFolders() {
        /**
         * Create folders to store observed data
         */
        String varPath = System.getProperty("user.home");
        filePathSim = varPath.concat("\\MeasuredCloudSimData\\");
        File fileSim = new File(filePathSim);
        FileSystemUtils.deleteRecursively(fileSim);
        fileSim.mkdir();

        filePathCl = varPath.concat("\\MeasuredCloudSimData\\cloudletsData\\");
        File fileCl = new File(filePathCl);
        FileSystemUtils.deleteRecursively(fileCl);
        fileCl.mkdir();

    }

    private void createLogFile() {

//        String filePath = "C:\\Users\\humai\\MeasuredData\\mappingComparison\\"; // "/home/humaira/Repositories/cloudsimPlusResults/mappingComparison/";
        try {
            String varPath = System.getProperty("user.home");
            filePathSim = varPath.concat("\\MeasuredCloudSimData\\");

            String filename = broker.getClass().getSimpleName();
            // create FileWriter object with file as parameter
            String logFileName = filePathSim + filename + "_SimulationResults" + "_arrvRate" + MEAN_CUSTOMERS_ARRIVAL_PER_MINUTE + "_totCloudlets" + dynAndstaCloudlets;
            File fileSim = new File(logFileName);

            FileWriter outputfile = new FileWriter(logFileName, true);

            // create CSVWriter with '|' as separator
            CSVWriter writer = new CSVWriter(outputfile, ',',
                CSVWriter.NO_QUOTE_CHARACTER,
                CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                CSVWriter.DEFAULT_LINE_END);

            // adding header to csv
            String[] header = {"No. of Repetition", "Broker Name", "Simulation Finish Time", "Total No. of Hosts",
                "No. of Idle Hosts", "Sum of Cloudlet Lengths on Hosts", "No. of VMs", "No. of Cloudets Submitted",
                "No. Cloudlets Finished", "CPU utili.(%)", "Total sum host time (sec)", "Power Consumed(kWh)"}; // "Cost",
            //"Total sum host time (sec)" is not the simulation time but instead it is the summation of processing time of all hosts (whihc although was running in parallel)
            writer.writeNext(header);

            writer.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * Log all data related to cloudlets
     * @param broker
     * @param numRep
     */
    private void logCloudletData(DatacenterBroker broker, int numRep){
        final List<Cloudlet> finishedCloudlets = broker.getCloudletFinishedList();
//            finishedCloudlets.sort(Comparator.comparingLong(Cloudlet::getId));
//            new CloudletsTableBuilder(finishedCloudlets).build();
        /** Store all data related to cloudlets
         *  Now get arrival, execution and finish time for cloudlets
         *
         */
//            Path rootPath = Paths.get("/home/humaira/Repositories/cloudsimPlusResults/");
        try {
            String varPath = System.getProperty("user.home");
            filePathCl = varPath.concat("/MeasuredCloudSimData/cloudletsData/");
            String timeFileName = filePathCl + broker.getClass().getSimpleName() + "_CloudletTimeData_numRep" + numRep + "_arrvRate" + MEAN_CUSTOMERS_ARRIVAL_PER_MINUTE + "_totCloudlets" + dynAndstaCloudlets;
            // create FileWriter object with file as parameter
            FileWriter outputfile = new FileWriter(timeFileName, false);
            // create CSVWriter with ',' as separator
            CSVWriter writer = new CSVWriter(outputfile, ',',
                CSVWriter.NO_QUOTE_CHARACTER,
                CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                CSVWriter.DEFAULT_LINE_END);
            // adding header to csv
            String[] header = {"Cloudlet Id", "Cloudlet Total Length", "Arrival Time", "Execution Time", "Finish Time", "Processing Time", "Due Time", "Deadline", "Host ID", "VM Id"};
            writer.writeNext(header);
//              // create a List which contains String array
            List<String[]> data = new ArrayList<String[]>();
            for (Cloudlet cloudlet : finishedCloudlets) {
                // add data to array list
                data.add(new String[]{Double.toString(cloudlet.getId()), Double.toString(cloudlet.getTotalLength()),
                    Double.toString(cloudlet.getSubmissionTime()), Double.toString(cloudlet.getExecStartTime()),
                    Double.toString(cloudlet.getFinishTime()), String.valueOf(cloudlet.getActualCpuTime()),
                    String.valueOf(cloudlet.getDueTime()), String.valueOf(cloudlet.getLifeTime()),
                    String.valueOf(cloudlet.getVm().getHost().getId()), String.valueOf(cloudlet.getVm().getId())});
                int hInd = (int) cloudlet.getVm().getHost().getId();
                double jh = (double) cloudlet.getTotalLength();
                hostsTotClLen[hInd] = hostsTotClLen[hInd] + jh;
            }
            writer.writeAll(data);
            writer.close();
            numOfFinishedCloudlets = finishedCloudlets.size();
            if(verbose) {
                System.out.printf("total cloudlet submitted services : %d\n", cloudletList.size());
                System.out.printf("total cloudlet finished services : %d\n", numOfFinishedCloudlets);
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * Log all data of whole system
     * @param hostPowkWh
     * @param brokersMappingCost
     * @param numRep
     * @param finishedCLoudlets
     */

    private void datalogging(double[] hostPowkWh, double[] brokersMappingCost, int numRep, int finishedCLoudlets) {
//        String filePath = "C:\\Users\\humai\\MeasuredData\\mappingComparison\\"; //"/home/humaira/Repositories/cloudsimPlusResults/mappingComparison/";
        try {
            String varPath = System.getProperty("user.home");
            filePathSim = varPath.concat("/MeasuredCloudSimData/");

            String filename = broker.getClass().getSimpleName();
            // create FileWriter object with file as parameter
            double cost = brokersMappingCost[0];
            double soluHeuTime = brokersMappingCost[1];
            String logFileName = filePathSim + filename + "_SimulationResults" + "_arrvRate" + MEAN_CUSTOMERS_ARRIVAL_PER_MINUTE + "_totCloudlets" + dynAndstaCloudlets;
            // create FileWriter object with file as parameter
            File fileSim = new File(logFileName);

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
            String[] data = {Integer.toString(numRep), broker.getClass().getSimpleName(),
                String.valueOf(simulation.getSimFinishTime()), Double.toString(arrAllHostPow.size()),
                Double.toString(idleHost), String.valueOf(hostTotIns), String.valueOf(vmList.size()), String.valueOf(cloudletList.size()),
                String.valueOf(finishedCLoudlets), String.valueOf(hostPowkWh[0]), String.valueOf(hostPowkWh[1]), String.valueOf(hostPowkWh[2])}; // String.valueOf(brokersMappingCost)
            writer.writeNext(data);
            writer.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
//
}

