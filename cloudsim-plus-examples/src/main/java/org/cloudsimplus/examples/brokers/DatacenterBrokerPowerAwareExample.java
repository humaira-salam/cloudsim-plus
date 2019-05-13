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
import org.apache.commons.lang3.ObjectUtils;
import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicy;
import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.brokers.DatacenterBroker;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerPowerAware;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerHeuristic;
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
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.ResourceProvisionerSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.schedulers.vm.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.util.SwfWorkloadFileReader;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModel;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic;
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
import org.cloudbus.cloudsim.power.models.PowerAware;
import org.cloudbus.cloudsim.power.models.PowerModel;
import org.cloudbus.cloudsim.power.models.PowerModelLinear;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

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
public class DatacenterBrokerPowerAwareExample {
    private static final int HOSTS_TO_CREATE = 100;
    private static final int VMS_TO_CREATE = 50;
    private static final int CLOUDLETS_TO_CREATE = 10;
    private static final int HOST_PES = 8;
    private static final int VM_PES = 1;
    private static final int CLOUDLET_PES = 1;
    private double TIME_TO_CREATE_NEW_CLOUDLET = 2;
    private static int CLOUDLET_LENGTH;
    private static boolean cloudletCreated;
    final List<Cloudlet> cloudletslist = new ArrayList<>(CLOUDLETS_TO_CREATE);


    /**
     * Simulated Annealing (SA) parameters.
     */
    public static final double SA_INITIAL_TEMPERATURE = 1.0;
    public static final double SA_COLD_TEMPERATURE = 0.0001;
    public static final double SA_COOLING_RATE = 0.003;
    public static final int    SA_NUMBER_OF_NEIGHBORHOOD_SEARCHES = 50;

    /**
     * time to finish terminate teh simulation
     *
     */
//    private double TIME_TO_FINISH_SIMULATION = 5000;


    private final Simulation simulation;
    private List<Cloudlet> cloudletList;
    //private final boolean showAllHostUtilizationHistoryEntries;
    private List<Vm> vmList;
    private final List<Host> hostList = new ArrayList<>();
    private List<Double> arrAllHostPow = new ArrayList<>();

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
     * Defines the minimum percentage of power a Host uses,
     * even it it's idle.
     */
    private static final double STATIC_POWER_PERCENT = 0.38; //0.7

    /**
     * The max number of watt-second (Ws) of power a Host uses.
     */
    private static final int MAX_POWER_WATTS_SEC = 85; //50

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
    private int maximumNumberOfCloudletsToCreateFromTheWorkloadFile = -1;

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


        final long seed = 0;
        final boolean showTables = true;
        final boolean showAllHostUtilizationHistoryEntries = true;
        // Heuristic
        cloudletCreated = false;
        final CloudSim simulation0 = new CloudSim();
        final UniformDistr random0 = new UniformDistr(0, 1, seed);
        final DatacenterBrokerHeuristic broker0 = createHeuristicBroker(simulation0, random0);
        new DatacenterBrokerPowerAwareExample(broker0, random0, showTables, showAllHostUtilizationHistoryEntries);


        // BestFit
        cloudletCreated = false;
        final CloudSim simulation1 = new CloudSim();
        final UniformDistr random1 = new UniformDistr(0, 1, seed);
        final DatacenterBroker broker1 = new DatacenterBrokerPowerAware(simulation1);
        new DatacenterBrokerPowerAwareExample(broker1, random1, showTables, showAllHostUtilizationHistoryEntries);




//        // Simple - RoundRobin
//        final CloudSim simulation2 = new CloudSim();
//        final UniformDistr random2 = new UniformDistr(0, 1, seed);
//        final DatacenterBroker broker2 = new DatacenterBrokerSimple(simulation2);
//        new DatacenterBrokerPowerAwareExample(broker2, random2, showTables);
    }

    /**
     * Default constructor where the simulation is built.
     */
    public DatacenterBrokerPowerAwareExample(final DatacenterBroker brkr, final ContinuousDistribution rand, final boolean showTables, final boolean showAllHostUtilizationHistoryEntries) {
        //Enables just some level of log messages.
        Log.setLevel(Level.ERROR);
//        this.showAllHostUtilizationHistoryEntries = showAllHostUtilizationHistoryEntries;


        System.out.println("Starting " + getClass().getSimpleName());

        broker = brkr;
        simulation = broker.getSimulation();
        random = rand;
//        simulation.terminateAt(TIME_TO_FINISH_SIMULATION);

        final Datacenter datacenter = createDatacenter(simulation);

        vmList = createVms(random);
        cloudletList = createCloudlets(random);


            /*Vms and cloudlets are created before the Datacenter and host
            because the example is defining the hosts based on VM requirements
            and VMs are created based on cloudlet requirements.*/
//        createCloudletsFromWorkloadFile();
        /**
         * Submit the created list of VMs and cloudlets to the broker
         */
        broker.submitVmList(vmList);
        broker.submitCloudletList(cloudletList);

        /**
         * on every clock tick check if the ocndition for generating dynamic cloudlet is satisfied
         */
        simulation.addOnClockTickListener(this::createDynamicCloudlet);
        simulation.start();

        // print simulation results
        if (showTables) {
            final List<Cloudlet> finishedCloudlets = broker.getCloudletFinishedList();
            finishedCloudlets.sort(Comparator.comparingLong(Cloudlet::getId));
            new CloudletsTableBuilder(finishedCloudlets).build();
        }
        printHostsCpuUtilizationAndPowerConsumption(showAllHostUtilizationHistoryEntries);
        //printVmsCpuUtilizationAndPowerConsumption();
        print();
    }

    private static DatacenterBrokerHeuristic createHeuristicBroker(final CloudSim sim, final ContinuousDistribution rand) {
        CloudletToVmMappingSimulatedAnnealing heuristic = createSimulatedAnnealingHeuristic(rand);
        final DatacenterBrokerHeuristic broker = new DatacenterBrokerHeuristic(sim);
        broker.setHeuristic(heuristic);
        return broker;
    }

    private List<Cloudlet> createCloudlets(final ContinuousDistribution rand) {
        for(int i = 0; i < CLOUDLETS_TO_CREATE; i++){
            cloudletslist.add(createCloudlet(getRandomPesNumber(4, rand)));
        }

        return cloudletslist;
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

    private void print() {
        final double brokersMappingCost = computeBrokersMappingCost(false);
        final double basicRoundRobinCost = computeRoundRobinMappingCost(false);
        System.out.printf(
            "The solution based on %s mapper costs %.2f. Basic round robin implementation in this example costs %.2f.\n", broker.getClass().getSimpleName(), brokersMappingCost, basicRoundRobinCost);
        System.out.println(getClass().getSimpleName() + " finished!");
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
        vmAllocationPolicy.setFindHostForVmFunction(this::findEnergyEffectiveHostForVm);

//        return new DatacenterSimple(sim, hostList, new VmAllocationPolicySimple());

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

        final PowerModel powerModel = new PowerModelLinear(MAX_POWER_WATTS_SEC, STATIC_POWER_PERCENT);

        createdHosts++;
        return new HostSimple(ram, bw, storage, peList)
            .setRamProvisioner(new ResourceProvisionerSimple())
            .setBwProvisioner(new ResourceProvisionerSimple())
            .setVmScheduler(new VmSchedulerTimeShared()).setPowerModel(powerModel);
    }

    private Vm createVm(final int pesNumber) {
        final long mips = 1000;
        final long   storage = 10000; // vm image size (Megabyte)
        final int    ram = 512; // vm memory (Megabyte)
        final long   bw = 1000; // vm bandwidth
        Vm vm = new VmSimple(createdVms++, mips, pesNumber)
            .setRam(ram).setBw(bw).setSize(storage)
            .setCloudletScheduler(new CloudletSchedulerTimeShared());

        vm.getUtilizationHistory().enable();
        return vm;
    }

    private Cloudlet createCloudlet(final int numberOfPes) {
        final long length = 400000; //in Million Structions (MI)
        final long fileSize = 300; //Size (in bytes) before execution
        final long outputSize = 300; //Size (in bytes) after execution
        CLOUDLET_LENGTH =  ran.nextInt(500000) + 25000;

        //Defines how CPU, RAM and Bandwidth resources are used
        //Sets the same utilization model for all these resources.
        final UtilizationModel utilization = new UtilizationModelFull();

        return new CloudletSimple(createdCloudlets++, CLOUDLET_LENGTH, numberOfPes)
            .setFileSize(fileSize)
            .setOutputSize(outputSize)
            .setUtilizationModel(utilization);
    }

    private void createCloudletsFromWorkloadFile() {
        final String fileName = WORKLOAD_BASE_DIR + WORKLOAD_FILENAME;
        SwfWorkloadFileReader reader = SwfWorkloadFileReader.getInstance(fileName, 1000);
        reader.setMaxLinesToRead(maximumNumberOfCloudletsToCreateFromTheWorkloadFile);
        this.cloudletList = reader.generateWorkload();
        broker.submitCloudletList(cloudletList);

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
        boolean findflag = cloudletCreated;
        if (!cloudletCreated && (simulation.clock() >= TIME_TO_CREATE_NEW_CLOUDLET)) {
            System.out.printf("\n# Dynamically creating 1 Cloudlet at time %.2f\n", evt.getTime());

            for (int i = 0; i < 4; i++) {
                cloudletList = createCloudlets(random);
            }
            broker.submitCloudletList(cloudletList);
            cloudletCreated = true;
        }
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
        int i = 0;
        for (Cloudlet c : cloudletList) {
            if (c.isBoundToVm()) {
                bestFitSolution.bindCloudletToVm(c, c.getVm());
            }
        }

        if (doPrint) {
            printSolution(
                "Best fit solution used by DatacenterBrokerSimple class",
                bestFitSolution, false);
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
    private void printHostsCpuUtilizationAndPowerConsumption(final boolean showAllHostUtilizationHistoryEntries) {
        System.out.println();
        for (final Host host : hostList) {
            double hostPow = printHostCpuUtilizationAndPowerConsumption(host, showAllHostUtilizationHistoryEntries);
            arrAllHostPow.add(hostPow);
        }
        System.out.printf("Sum of all Hosts mean power consumption : %.4f Watt-Sec \n", arrAllHostPow.stream().mapToDouble(a->a).sum());

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
            final double utilizationPercent = entry.getValue().getSum();
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
//            "Mean %.2f Watt-Sec for %d usage samples (%.5f KWatt-Hour)\n",
//            powerWattsSecMean, utilizationPercentHistory.size(), PowerAware.wattsSecToKWattsHour(powerWattsSecMean));
//        System.out.println("----------------------------------------------------------------------------------------------------------------------\n");
        return totalWattsSec;//powerWattsSecMean;
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
                    "\tTime %8.1f | Host CPU Usage: %6.1f%% (%6.1f MIPs) | VM CPU Usage: %6.1f%% (%6.1f MIPs) | Power Consumption: %8.0f Watt-Sec * %6.0f Secs = %10.2f Watt-Sec\n",
                    time, vmUtilInHost * 100, vmUtilInHost*vm.getHost().getTotalMipsCapacity(), entry.getValue() * 100, entry.getValue() * vm.getTotalMipsCapacity(),vmPower, utilizationHistoryTimeInterval, wattsPerInterval);
                prevTime = time;
            }
            System.out.println();
        }
    }

}
