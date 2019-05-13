package org.cloudbus.cloudsim.brokers;

import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.events.SimEvent;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.hosts.Host;

import java.util.*;


/**
 * <p>A simple implementation of {@link DatacenterBroker} that uses a best fit
 * mapping among submitted cloudlets and Vm's.
 * The Broker then places the submitted Vm's at the first Datacenter found.
 * If there isn't capacity in that one, it will try the other ones.</p>
 *
 * @author Humaira Abdul Salam
 * @since CloudSim Plus 4.3.8
 */
public class DatacenterBrokerPowerAware extends DatacenterBrokerSimple {

    double[] sumMipsonHost = new double[100]; // need to improve this as , make it to the size of host list

//    private List<Double> sumMipsonHost = new ArrayList<>();


    /**
     * Creates a new DatacenterBroker object.
     *
     * @param simulation The CloudSim instance that represents the simulation the Entity is related to
     */
    public DatacenterBrokerPowerAware(final CloudSim simulation) {
        super(simulation);
    }

    /**
     * Processes the end of execution of a given cloudlet inside a Vm.
     *
     * @param evt the cloudlet that has just finished to execute and was returned to the broker
     */
    @Override
    protected void processCloudletReturn(final SimEvent evt) {
        final Cloudlet cloudlet = (Cloudlet) evt.getData();
        long freeVmsPes = cloudlet.getVm().getExpectedFreePesNumber() + cloudlet.getNumberOfPes();
        cloudlet.getVm().setExpectedFreePesNumber(freeVmsPes);
        super.processCloudletReturn(evt);
    }

    /**
     * Selects the VM with the lowest number of PEs that is able to run a given Cloudlet.
     * In case the algorithm can't find such a VM, it uses the
     * default DatacenterBroker VM mapper as a fallback.
     *
     * @param cloudlet the Cloudlet to find a VM to run it
     * @return the VM selected for the Cloudlet or {@link Vm#NULL} if no suitable VM was found
     */
    @Override
    public Vm defaultVmMapper(final Cloudlet cloudlet) {
        if (cloudlet.isBoundToCreatedVm()) {
            return cloudlet.getVm();
        }

        Vm posVm = mapVmToCloudlet(cloudlet);

        if(posVm != Vm.NULL){
            double hostpower = getPowerOfHost(posVm);
            posVm.setExpectedFreePesNumber(posVm.getExpectedFreePesNumber()-cloudlet.getNumberOfPes());
            LOGGER.debug("{}: {}: {} (PEs: {}) mapped to {} (available PEs: {}, tot PEs: {}, host power : {})", getSimulation().clock(), getName(),
                cloudlet, cloudlet.getNumberOfPes(), posVm, posVm.getExpectedFreePesNumber(), posVm.getFreePesNumber(), hostpower);
        }
        else
        {
            LOGGER.warn("{}: {}: {} (PEs: {}) couldn't be mapped to any VM",
                getSimulation().clock(), getName(), cloudlet, cloudlet.getNumberOfPes());
        }
        return posVm;
    }

    private Vm mapVmToCloudlet(final Cloudlet cl) {

        return getVmCreatedList()
                    .stream()
                    .filter(x-> getPowerOfHost(x) < 75)
                    .filter(x -> x.getExpectedFreePesNumber() >= cl.getNumberOfPes())
                    .min(Comparator.comparingLong(x -> x.getExpectedFreePesNumber()))
                    .orElse(Vm.NULL);
    }


    /** get list of host here
     * and estimate the power of host where VM will have new cloudlet . if the host power threshold exceeds
     * then do not assign this cloudlet to this VM and search from other available VMs
     */
    private double getPowerOfHost(final Vm vm) {
        Host selHost = vm.getHost();
        double expUsedHostPEs= 0;
        double wattsSec = 0;
        List<Vm> vmListOnSelHost = selHost.getVmList();
        for (Vm vmOnHost : vmListOnSelHost) {
            long extPE1 = vmOnHost.getNumberOfPes();
            long extPE2 = vmOnHost.getExpectedFreePesNumber();
            expUsedHostPEs = (double)(expUsedHostPEs + vmOnHost.getNumberOfPes()-vmOnHost.getExpectedFreePesNumber());
        }
        wattsSec = selHost.getPowerModel().getPower(expUsedHostPEs/selHost.getNumberOfPes());
        int indexHostPow = (int)selHost.getId();
        sumMipsonHost[indexHostPow] = wattsSec;
        LOGGER.debug("{}: {}: On host: {}, Host's Total PEs: {}, Host Used PEs: {}, and Host Power at this point is: {}",
            getSimulation().clock(), getName(), selHost, selHost.getNumberOfPes(), expUsedHostPEs, wattsSec);

        return wattsSec ;
    }

}
