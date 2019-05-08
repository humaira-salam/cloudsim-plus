

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
package org.cloudsimplus.examples.power;

import ch.qos.logback.classic.Level;
import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicy;
import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.brokers.DatacenterBroker;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.distributions.UniformDistr;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.power.models.PowerAware;
import org.cloudbus.cloudsim.power.models.PowerModel;
import org.cloudbus.cloudsim.power.models.PowerModelLinear;
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
import org.cloudsimplus.examples.simulationstatus.TerminateSimulationAtGivenTimeExample;
import org.cloudsimplus.listeners.EventInfo;
import org.cloudsimplus.util.Log;
import org.cloudbus.cloudsim.distributions.ContinuousDistribution;


import java.util.*;
import java.util.stream.Collectors;


/**
 * An example to show power consumption of Hosts and VMs.
 * Realize that for this goal, you define a {@link PowerModel}
 * for each Host by calling {@code host.setPowerModel(powerModel)}.
 *
 * <p>It creates the number of cloudlets defined in
 * {@link #CLOUDLETS}. All cloudlets will required 100% of PEs they are using all the time.
 * Half of these cloudlets are created with the length defined by {@link #CLOUDLET_LENGTH}
 * and the other half will have the double of this length.
 * This way, it's possible to see that for the last half of the
 * simulation time, a Host doesn't use the entire CPU capacity,
 * and therefore doesn't consume the maximum power.</p>
 *
 * <p>However, you may notice in this case that the power usage isn't
 * half of the maximum consumption, because there is a minimum
 * amount of power to use, even if the Host is idle,
 * which is defined by {@link #STATIC_POWER_PERCENT}.
 * In the case of the {@link PowerModelLinear},
 * there is a constant power which is computed
 * and added to consumer power when it
 * is lower or equal to the minimum usage percentage.</p>
 *
 * <p>Realize that the Host CPU Utilization History is only computed
 * if VMs utilization history is enabled by calling
 * {@code vm.getUtilizationHistory().enable()}</p>
 *
 * <p>Each line in the table with CPU utilization and power consumption shows
 * the data from the time specified in the line up to the time before the value in the next line.
 * For instance, consider the scheduling interval is 10, the time in the first line is 0 and
 * it shows 100% CPU utilization and 100 Watt-Sec of power consumption.
 * Then, the next line contains data for time 10.
 * That means between time 0 and time 9 (from time 0 to 9 we have 10 samples),
 * the CPU utilization and power consumption
 * is the one provided for time 0.</p>
 *
 * @author Manoel Campos da Silva Filho
 * @see VmsRamAndBwUsageExample
 * @see org.cloudsimplus.examples.resourceusage.VmsCpuUsageExample
 * @since CloudSim Plus 1.2.4
 */
public class VMPowerModeling {
    /**
     * Defines, between other things, the time intervals
     * to keep Hosts CPU utilization history records.
     */
    private static final int SCHEDULING_INTERVAL = 1;
    private static final int HOSTS = 2;
    private static final int HOST_PES = 4;

    private static final int VMS = 2;
    private static final int VM_PES = 1;


    private static Random ran = new Random();
    private static final int CLOUDLETS = 6;
    private static final int CLOUDLET_PES = 1;
    private static int CLOUDLET_LENGTH = ran.nextInt(6) + 5; //new UniformDistr(25000,70000); //65000; //MI for 10 % is 7.3*e11

    private static boolean vmCreated = false;
    private static boolean cloudletCreated = false;

    //variable for random scheduling of VM on hosts and random cloudlets length
    private final ContinuousDistribution random;

    /**
     * Defines the minimum percentage of power a Host uses,
     * even it it's idle.
     */
    private static final double STATIC_POWER_PERCENT = 0.38; //0.7

    /**
     * The max number of watt-second (Ws) of power a Host uses.
     */
    private static final int MAX_POWER_WATTS_SEC = 85; //50
    private double TIME_TO_CREATE_NEW_CLOUDLET = 4;
    private double TIME_TO_CREATE_NEW_VM = 2;

    private double TIME_TO_FINISH_SIMULATION = 150;//TIME_TO_CREATE_NEW_CLOUDLET*15;

    private final CloudSim simulation;
    private DatacenterBroker broker0;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;
    private Datacenter datacenter0;
    private final List<Host> hostList;
    private long VmMipstotHost;
    double[][] HostTotMips= new double[2][2];
    /**
     * If set to false, consecutive lines with the the same CPU utilization and power consumption
     * will be shown only once, at the time that such metrics started to return those values.
     * The last history line is always shown, independent of any condition.
     */
    private boolean showAllHostUtilizationHistoryEntries;


    public static void main(String[] args) {
        new VMPowerModeling(true);
    }

    private VMPowerModeling(boolean showAllHostUtilizationHistoryEntries) {
        Log.setLevel(Level.ALL);
        this.showAllHostUtilizationHistoryEntries = showAllHostUtilizationHistoryEntries;

        simulation = new CloudSim();
        simulation.terminateAt(TIME_TO_FINISH_SIMULATION);

        random = new UniformDistr(-1, 2);


        hostList = new ArrayList<>(HOSTS);
        datacenter0 = createDatacenterSimple();
        //Creates a broker that is a software acting on behalf of a cloud customer to manage his/her VMs and Cloudlets
        broker0 = new DatacenterBrokerSimple(simulation);


        /* If you comment this line, the default mapping policy will be used.
         * This way, you'll see that Cloudlet 0 is mapped to VM 0, Cloudlet 1 to VM 1 and so on. */
       // broker0.setVmMapper(this::bestFitCloudletToVmMapper);


        vmList = createVms();
        broker0.submitVmList(vmList);

        //createAndSubmitVmAndCloudlets();
        /*Defines a delay of 5 seconds and creates another group of cloudlets
        that will start executing inside a VM only after this delay expires.*/
        double submissionDelay = 5;

        cloudletList = createCloudlets();
        broker0.submitCloudletList(cloudletList,0);
        //broker0.submitCloudletList(cloudletList,2);

        runSimulationAndPrintResults();
        System.out.println(getClass().getSimpleName() + " finished!");

    }

    private void runSimulationAndPrintResults() {
        simulation.addOnClockTickListener(this::createDynamicCloudlet);
        simulation.addOnClockTickListener(this::createDynamicVm);

        simulation.start();
        System.out.println("------------------------------- SIMULATION FOR SCHEDULING INTERVAL = " + SCHEDULING_INTERVAL + " -------------------------------");

        List<Cloudlet> cloudlets = broker0.getCloudletFinishedList();
        new CloudletsTableBuilder(cloudlets).build();
        printHostsCpuUtilizationAndPowerConsumption();

        printVmsCpuUtilizationAndPowerConsumption();
    }

    /**
     * Prints the following information from VM's history:
     * <ul>
     * <li>VM's CPU utilization relative to the total Host's CPU utilization.
     * For instance, if there are 2 equal VMs using 100% of their CPU, the utilization
     * of each one corresponds to 50% of the Host's CPU utilization.</li>
     * <li>VM's power consumption relative to the total Host's power consumption.</li>
     * </ul>
     * <p>
     * If we just get the percentage of CPU the VM is using from the Host
     * (as demonstrated above) and compute the VM power consumption we'll get an wrong value.
     *
     * <p>A Host, even if idle, may consume a static amount of power.
     * Lets say it consumes 20 watt-sec in idle state and that for each 1% of CPU use it consumes 1 watt-sec more.
     * For the 2 VMs of the example above, each one using 50% of CPU will consume 50 watt-sec.
     * That is 100 watt-sec for the 2 VMs, plus the 20 watt-sec that is static.
     * Therefore we have a total Host power consumption of 120 watt-sec.
     * </p>
     *
     * <p>
     * If we computer the power consumption for a single VM by
     * calling {@code vm.getHost().getPowerModel().getPower(hostCpuUsage)},
     * we get the 50 watt-sec consumed by the VM, plus the 20 watt-sec of static power.
     * This adds up to 70 watt-sec. If the two VMs are equal and using the same amount of CPU,
     * their power consumption would be the half of the total Host's power consumption.
     * This would be 60 watt-sec, not 70.
     * <p>
     * This way, we have to compute VM power consumption by sharing a supposed Host static power
     * consumption with each VM, as it's being shown here.
     * Not all {@link PowerModel} have this static power consumption.
     * However, the way the VM power consumption
     * is computed here, that detail is abstracted.
     * </p>
     */
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

    /**
     * The Host CPU Utilization History is only computed
     * if VMs utilization history is enabled by calling
     * {@code vm.getUtilizationHistory().enable()}.
     */
    private void printHostsCpuUtilizationAndPowerConsumption() {
        System.out.println();
        for (final Host host : hostList) {
            printHostCpuUtilizationAndPowerConsumption(host);
        }
    }

    private void printHostCpuUtilizationAndPowerConsumption(final Host host) {
        System.out.printf("Host %d CPU utilization and power consumption\n", host.getId());
        System.out.println("----------------------------------------------------------------------------------------------------------------------");
        final Map<Double, DoubleSummaryStatistics> utilizationPercentHistory = host.getUtilizationHistory();
        double totalPowerWattsSec = 0;
        double prevUtilizationPercent = -1, prevWattsPerInterval = -1;
        //time difference from the current to the previous line in the history
        double utilizationHistoryTimeInterval;
        double prevTime = 0;
        for (Map.Entry<Double, DoubleSummaryStatistics> entry : utilizationPercentHistory.entrySet()) {
            utilizationHistoryTimeInterval = entry.getKey() - prevTime;
            //The total Host's CPU utilization for the time specified by the map key
            final double utilizationPercent = entry.getValue().getSum();
            final double wattsSec = host.getPowerModel().getPower(utilizationPercent);
            final double wattsPerInterval = wattsSec * utilizationHistoryTimeInterval;
            totalPowerWattsSec += wattsPerInterval;
            //only prints when the next utilization is different from the previous one, or it's the first one
            if (showAllHostUtilizationHistoryEntries || prevUtilizationPercent != utilizationPercent || prevWattsPerInterval != wattsPerInterval) {
                System.out.printf("\tTime %8.1f | Host CPU Usage: %6.1f%% (%6.1f MIPs) | Power Consumption: %8.0f Watt-Sec * %6.0f Secs = %10.2f Watt-Sec\n",
                   entry.getKey(), utilizationPercent * 100, utilizationPercent*host.getTotalMipsCapacity(), wattsSec, utilizationHistoryTimeInterval, wattsPerInterval);
            }
            prevUtilizationPercent = utilizationPercent;
            prevWattsPerInterval = wattsPerInterval;
            prevTime = entry.getKey();
        }

        System.out.printf(
            "Total Host %d Power Consumption in %.0f secs: %.0f Watt-Sec (%.5f KWatt-Hour)\n",
            host.getId(), simulation.clock(), totalPowerWattsSec, PowerAware.wattsSecToKWattsHour(totalPowerWattsSec));
        final double powerWattsSecMean = totalPowerWattsSec / simulation.clock();
        System.out.printf(
            "Mean %.2f Watt-Sec for %d usage samples (%.5f KWatt-Hour)\n",
            powerWattsSecMean, utilizationPercentHistory.size(), PowerAware.wattsSecToKWattsHour(powerWattsSecMean));
        System.out.println("----------------------------------------------------------------------------------------------------------------------\n");
    }



    /**
     * Creates a {@link Datacenter} and its {@link Host}s.
     */
    private Datacenter createDatacenterSimple() {
        for (int i = 0; i < HOSTS; i++) {
            Host host = createPowerHost();
            hostList.add(host);
        }

        //Replaces the default method that allocates Hosts to VMs by our own implementation
        VmAllocationPolicySimple vmAllocationPolicy = new VmAllocationPolicySimple();
        vmAllocationPolicy.setFindHostForVmFunction(this::findEnergyEffectiveHostForVm);

        final Datacenter dc = new DatacenterSimple(simulation, hostList, vmAllocationPolicy);
        //final Datacenter dc = new DatacenterSimple(simulation, hostList, new VmAllocationPolicySimple());
        dc.setSchedulingInterval(SCHEDULING_INTERVAL);
        return dc;
    }


//
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
        double avaMips;
        double totMips;
        double VmAllocMips;
        List<Double> allocMips;
        allocMips = new ArrayList<>();


        double thr = 20000;
        List<Host> EAhost;
        List<Host> myhost;
        myhost = vmAllocationPolicy
            .getHostList()
            .stream()
            .filter(host -> (host.isSuitableForVm(vm) && (host.getAvailableMips() >= thr) )).collect(Collectors.toList());
            //.min(Comparator.comparing(host -> (host.getMips()- host.getAvailableMips())));
            //.min(Comparator.comparing(Host :: host.getMips()-hostgetAvailableMips()));

            //.findAny();
        for(Host h : myhost){
            //Host hostname = h;
            //for(Vm vml : vmList) {
              //  Host hvm = vml.getHost();

            //final Map<Host, Long> getHostFreePesMap()
            List<Vm> VmLOnHost = h.getVmList();
            for(Vm VMh:VmLOnHost){
              //  double hosttotuse = h.getUtilizationOfCpuMips();
                double b = VMh.getTotalCpuMipsUsage();
                double c = VMh.getCurrentRequestedMaxMips();
                double d = VMh.getCpuPercentUsage();
                UtilizationHistory e = VMh.getUtilizationHistory();
                double vmtotcurrreq = VMh.getCurrentRequestedTotalMips();
                int a = (int) h.getId();
                HostTotMips[a][0] = h.getId();
                double prevMips = HostTotMips[a][1];
                HostTotMips[a][1] = prevMips+vmtotcurrreq;
                //System.out.println("Number of tot host Mips " + HostTotMips[1][1]);
            }
            List<Pe> bzPE = h.getBusyPeList();
            int NumBzPEs = bzPE.size();
            int VmOnHost = NumBzPEs/VM_PES;

            //System.out.println("Number of busy PEs at Host " + h.getId() + " is/are : " + NumBzPEs + " with " + VmOnHost + " active VMs ");
            // to find number of running VM on each host Divide active PEs on host by VMPes

            //  VmAllocMips = h.getTotalAllocatedMipsForVm(vml);

            //allocMips.add(VmAllocMips);
            //}

        }

            //avaMips = h.getTotalMipsCapacity();
           // double EApow = (totMips-avaMips) * 0.75;
           // double pow = HostCpuPowerConsumption(h);



   /*     for (Host h : hostList) {

            avaMips = h.getTotalMipsCapacity();
            if (avaMips > thr) {
                //EAhost.add(h);

            }*/
            //System.out.printf("busycore:%d -total cores:%d -Mips to VM : %d -Toto MIPS:%d -Avail MIPS:%d",bzPE, numPE, mipsVm, totMips, avaMips);
/*        List<Pe> bzPE = h.getBusyPeList();
        long numPE = h.getNumberOfPes();
        double mipsVm = h.getTotalAllocatedMipsForVm(vm);
        double totMips = h.getMips();
        double avaMips = h.getAvailableMips();
        System.out.println(avaMips);

        }

*/
        Optional<Host> mhost = vmAllocationPolicy
            .getHostList()
            .stream()
            .filter(host -> (host.isSuitableForVm(vm) && (host.getAvailableMips() >= thr) ))
            .min(Comparator.comparing(host -> (host.getMips()- host.getAvailableMips())));
        return mhost;
    }



    /*private Vm bestFitCloudletToVmMapper(final Cloudlet cloudlet) {
       Optional<Vm> vmd = cloudlet
            .getBroker()
            .getVmCreatedList()
            .stream()
            .filter(vm -> vm.getNumberOfPes() >= cloudlet.getNumberOfPes())
            .sorted().findAny();

        Vm vms = cloudlet.getBroker().getVmCreatedList().get(0);
        return vms;
    }*/



    private double  HostCpuPowerConsumption(Host host) {
        System.out.printf("Host %d CPU utilization and power consumption\n", host.getId());
        System.out.println("----------------------------------------------------------------------------------------------------------------------");
        final Map<Double, DoubleSummaryStatistics> utilizationPercentHistory = host.getUtilizationHistory();
        double totalPowerWattsSec = 0;
        double wattsSec = 0;
        //double prevUtilizationPercent = -1, prevWattsPerInterval = -1;
        //time difference from the current to the previous line in the history
        //double utilizationHistoryTimeInterval;
        //double prevTime = 0;
        for (Map.Entry<Double, DoubleSummaryStatistics> entry : utilizationPercentHistory.entrySet()) {
            //utilizationHistoryTimeInterval = entry.getKey() - prevTime;
            //The total Host's CPU utilization for the time specified by the map key
            final double utilizationPercent = entry.getValue().getSum();
            wattsSec = host.getPowerModel().getPower(utilizationPercent);
            //final double wattsPerInterval = wattsSec * utilizationHistoryTimeInterval;
            //totalPowerWattsSec += wattsPerInterval;
            //only prints when the next utilization is different from the previous one, or it's the first one
            //if (showAllHostUtilizationHistoryEntries || prevUtilizationPercent != utilizationPercent || prevWattsPerInterval != wattsPerInterval) {
                System.out.printf("\tTime %8.1f | Host CPU Usage: %6.1f%% (%6.1f MIPs) | Power Consumption: %8.0f Watt-Sec \n",
                    entry.getKey(), utilizationPercent * 100, utilizationPercent*host.getTotalMipsCapacity(), wattsSec);
            //}
            //prevUtilizationPercent = utilizationPercent;
            //prevWattsPerInterval = wattsPerInterval;
            //prevTime = entry.getKey();
        }

        System.out.printf(
            "Total Host %d Power Consumption in %.0f secs: %.0f Watt-Sec (%.5f KWatt-Hour)\n",
            host.getId(), simulation.clock(), totalPowerWattsSec, PowerAware.wattsSecToKWattsHour(wattsSec));
        final double powerWattsSecMean = totalPowerWattsSec / simulation.clock();
        System.out.printf(
            "Mean %.2f Watt-Sec for %d usage samples (%.5f KWatt-Hour)\n",
            powerWattsSecMean, utilizationPercentHistory.size(), PowerAware.wattsSecToKWattsHour(powerWattsSecMean));
        System.out.println("----------------------------------------------------------------------------------------------------------------------\n");

        return wattsSec;
    }

    /**
     * Simulates the dynamic arrival of a Cloudlet and a VM during simulation runtime.
     * @param evt
     */
    private void createDynamicCloudlet(final EventInfo evt) {
        System.out.printf("\n# Received OnClockTick evt-time: %.4f, sim-clock: %.4f\n", evt.getTime(), simulation.clock());

        for (Vm vm_i : vmList)
        {
            if (vm_i.isCreated())
            {
                System.out.printf("\n# %s - current MIPS usage: %.2f, current requested total MIPS: %.2f\n", vm_i, vm_i.getTotalCpuMipsUsage(), vm_i.getCurrentRequestedTotalMips());
            }
        }

        if (!cloudletCreated && (simulation.clock() >= TIME_TO_CREATE_NEW_CLOUDLET)) {
            System.out.printf("\n# Dynamically creating 1 Cloudlet at time %.2f\n", evt.getTime());

            for (int i = 0; i < 4; i++) {
                Cloudlet cloudlet = createCloudlet();
                cloudletList.add(cloudlet);
            }
            broker0.submitCloudletList(cloudletList, 0);
            //TIME_TO_CREATE_NEW_CLOUDLET = simulation.clock();
            cloudletCreated = true;
        }
    }

//    private void createCloudletsRandomly(final EventInfo evt){
//        if()
//
//    }


    private void createDynamicVm(final EventInfo evt) {
        if (!vmCreated && (simulation.clock() >= TIME_TO_CREATE_NEW_VM)) {
            System.out.printf("\n# Dynamically creating 1 VM at time %.2f\n", evt.getTime());

            for (int i = 0; i < 1; i++) {
                Vm vm = createVm();
                vmList.add(vm);
                broker0.submitVm(vm);
                //broker0.submitVmList(vmList, 0);
            }
            // TIME_TO_CREATE_NEW_CLOUDLET = simulation.clock();
            vmCreated = true;
        }
    }


    private Host createPowerHost() {
        final List<Pe> peList = new ArrayList<>(HOST_PES);
        //List of Host's CPUs (Processing Elements, PEs)
        for (int i = 0; i < HOST_PES; i++) {
            peList.add(new PeSimple(16020, new PeProvisionerSimple())); //16020 MI per second
        }

        final PowerModel powerModel = new PowerModelLinear(MAX_POWER_WATTS_SEC, STATIC_POWER_PERCENT);

        final long ram = 32141; //in Megabytes
        final long bw = 10000; //in Megabits/s // not known for my host
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

    /**
     * Creates a list of VMs.
     */
    private List<Vm> createVms() {
       final List<Vm> list = new ArrayList<>(VMS);
        for (int i = 0; i < VMS; i++) {
            list.add(createVm());
        }

        return list;
    }

    private Vm createVm() {
        Vm vm = new VmSimple(16020, VM_PES); //assuming each vm core take same host cpu utilization (already mapping somehow in some function)
        vm.setRam(1993).setBw(1000).setSize(49000)
            .setCloudletScheduler(new CloudletSchedulerTimeShared());
        vm.getUtilizationHistory().enable();

        return vm;
    }


    /**
     * Creates a list of Cloudlets.
     */

    private List<Cloudlet> createCloudlets() {
        final List<Cloudlet> list = new ArrayList<>(CLOUDLETS);
        for (int i = 0; i < CLOUDLETS; i++) {
            list.add(createCloudlet());
        }
        return list;
    }

    private Cloudlet createCloudlet() {
        UtilizationModel um = new UtilizationModelDynamic(0.2);
        CLOUDLET_LENGTH =  ran.nextInt(50000) + 25000;
        Cloudlet cloudlet =
            new CloudletSimple(CLOUDLET_LENGTH, CLOUDLET_PES)
            .setFileSize(1024)
            .setOutputSize(1024)
            .setUtilizationModelCpu(new UtilizationModelFull())
            .setUtilizationModelRam(um)
            .setUtilizationModelBw(um);
        return cloudlet;
    }



}
