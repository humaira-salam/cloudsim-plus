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
    /**
     * Creates a new DatacenterBroker object.
     *
     * @param simulation The CloudSim instance that represents the simulation the Entity is related to
     */
    public DatacenterBrokerPowerAware(final CloudSim simulation) { super(simulation); }

    /**
     * Selects the VM with the lowest number of PEs that is able to run a given Cloudlet and
     * with the host power of max 75W.
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

        final Vm mappedVm = getVmCreatedList()
            .stream()
            .filter(x-> getPowerOfHost(x) < 75)
            .filter(x -> x.getExpectedFreePesNumber() >= cloudlet.getNumberOfPes())
            .min(Comparator.comparingLong(x -> x.getExpectedFreePesNumber()))
            .orElse(Vm.NULL);

        if(mappedVm != Vm.NULL){
            LOGGER.debug("{}: {}: {} (PEs: {}) mapped to {} (available PEs: {}, tot PEs: {})",
                getSimulation().clock(), getName(), cloudlet, cloudlet.getNumberOfPes(), mappedVm,
                mappedVm.getExpectedFreePesNumber(), mappedVm.getFreePesNumber());
        }
        else
        {
            LOGGER.warn(": {}: {}: {} (PEs: {}) couldn't be mapped to any VM",
                getSimulation().clock(), getName(), cloudlet, cloudlet.getNumberOfPes());
        }
        return mappedVm;
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
            expUsedHostPEs = expUsedHostPEs + vmOnHost.getNumberOfPes() - vmOnHost.getExpectedFreePesNumber();
        }
        wattsSec = selHost.getPowerModel().getPower(expUsedHostPEs/selHost.getNumberOfPes());
        LOGGER.debug("{}: {}: On host: {}, Host's Total PEs: {}, Host Used PEs: {}, and Host Power at this point is: {}",
            getSimulation().clock(), getName(), selHost, selHost.getNumberOfPes(), expUsedHostPEs, wattsSec);

        return wattsSec ;
    }

}
