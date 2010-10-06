package net.sf.openrocket.document;

import java.util.ArrayList;
import java.util.List;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import net.sf.openrocket.aerodynamics.AerodynamicCalculator;
import net.sf.openrocket.aerodynamics.BarrowmanCalculator;
import net.sf.openrocket.aerodynamics.WarningSet;
import net.sf.openrocket.logging.LogHelper;
import net.sf.openrocket.masscalc.BasicMassCalculator;
import net.sf.openrocket.masscalc.MassCalculator;
import net.sf.openrocket.rocketcomponent.Configuration;
import net.sf.openrocket.rocketcomponent.Rocket;
import net.sf.openrocket.simulation.BasicEventSimulationEngine;
import net.sf.openrocket.simulation.FlightData;
import net.sf.openrocket.simulation.GUISimulationConditions;
import net.sf.openrocket.simulation.RK4SimulationStepper;
import net.sf.openrocket.simulation.SimulationConditions;
import net.sf.openrocket.simulation.SimulationEngine;
import net.sf.openrocket.simulation.SimulationStepper;
import net.sf.openrocket.simulation.exception.SimulationException;
import net.sf.openrocket.simulation.exception.SimulationListenerException;
import net.sf.openrocket.simulation.listeners.SimulationListener;
import net.sf.openrocket.startup.Application;
import net.sf.openrocket.util.BugException;
import net.sf.openrocket.util.ChangeSource;


public class Simulation implements ChangeSource, Cloneable {
	private static final LogHelper log = Application.getLogger();
	
	public static enum Status {
		/** Up-to-date */
		UPTODATE,

		/** Loaded from file, status probably up-to-date */
		LOADED,

		/** Data outdated */
		OUTDATED,

		/** Imported external data */
		EXTERNAL,

		/** Not yet simulated */
		NOT_SIMULATED
	}
	
	
	private final Rocket rocket;
	
	private String name = "";
	
	private Status status = Status.NOT_SIMULATED;
	
	/** The conditions to use */
	// TODO: HIGH: Change to use actual conditions class??
	private GUISimulationConditions conditions;
	
	private ArrayList<String> simulationListeners = new ArrayList<String>();
	
	private final Class<? extends SimulationEngine> simulationEngineClass = BasicEventSimulationEngine.class;
	private Class<? extends SimulationStepper> simulationStepperClass = RK4SimulationStepper.class;
	private Class<? extends AerodynamicCalculator> aerodynamicCalculatorClass = BarrowmanCalculator.class;
	private Class<? extends MassCalculator> massCalculatorClass = BasicMassCalculator.class;
	


	/** Listeners for this object */
	private List<ChangeListener> listeners = new ArrayList<ChangeListener>();
	

	/** The conditions actually used in the previous simulation, or null */
	private GUISimulationConditions simulatedConditions = null;
	private String simulatedMotors = null;
	private FlightData simulatedData = null;
	private int simulatedRocketID = -1;
	
	
	/**
	 * Create a new simulation for the rocket.  The initial motor configuration is
	 * taken from the default rocket configuration.
	 * 
	 * @param rocket	the rocket associated with the simulation.
	 */
	public Simulation(Rocket rocket) {
		this.rocket = rocket;
		this.status = Status.NOT_SIMULATED;
		
		conditions = new GUISimulationConditions(rocket);
		conditions.setMotorConfigurationID(
				rocket.getDefaultConfiguration().getMotorConfigurationID());
		conditions.addChangeListener(new ConditionListener());
	}
	
	
	public Simulation(Rocket rocket, Status status, String name, GUISimulationConditions conditions,
			List<String> listeners, FlightData data) {
		
		if (rocket == null)
			throw new IllegalArgumentException("rocket cannot be null");
		if (status == null)
			throw new IllegalArgumentException("status cannot be null");
		if (name == null)
			throw new IllegalArgumentException("name cannot be null");
		if (conditions == null)
			throw new IllegalArgumentException("conditions cannot be null");
		
		this.rocket = rocket;
		
		if (status == Status.UPTODATE) {
			this.status = Status.LOADED;
		} else if (data == null) {
			this.status = Status.NOT_SIMULATED;
		} else {
			this.status = status;
		}
		
		this.name = name;
		
		this.conditions = conditions;
		conditions.addChangeListener(new ConditionListener());
		
		if (listeners != null) {
			this.simulationListeners.addAll(listeners);
		}
		

		if (data != null && this.status != Status.NOT_SIMULATED) {
			simulatedData = data;
			if (this.status == Status.LOADED) {
				simulatedConditions = conditions.clone();
				simulatedRocketID = rocket.getModID();
			}
		}
		
	}
	
	
	/**
	 * Return the rocket associated with this simulation.
	 * 
	 * @return	the rocket.
	 */
	public Rocket getRocket() {
		return rocket;
	}
	
	
	/**
	 * Return a newly created Configuration for this simulation.  The configuration
	 * has the motor ID set and all stages active.
	 * 
	 * @return	a newly created Configuration of the launch conditions.
	 */
	public Configuration getConfiguration() {
		Configuration c = new Configuration(rocket);
		c.setMotorConfigurationID(conditions.getMotorConfigurationID());
		c.setAllStages();
		return c;
	}
	
	/**
	 * Returns the simulation conditions attached to this simulation.  The conditions
	 * may be modified freely, and the status of the simulation will change to reflect
	 * the changes.
	 * 
	 * @return the simulation conditions.
	 */
	public GUISimulationConditions getConditions() {
		return conditions;
	}
	
	
	/**
	 * Get the list of simulation listeners.  The returned list is the one used by
	 * this object; changes to it will reflect changes in the simulation.
	 * 
	 * @return	the actual list of simulation listeners.
	 */
	public List<String> getSimulationListeners() {
		return simulationListeners;
	}
	
	
	/**
	 * Return the user-defined name of the simulation.
	 * 
	 * @return	the name for the simulation.
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * Set the user-defined name of the simulation.  Setting the name to
	 * null yields an empty name.
	 * 
	 * @param name	the name of the simulation.
	 */
	public void setName(String name) {
		if (this.name.equals(name))
			return;
		
		if (name == null)
			this.name = "";
		else
			this.name = name;
		
		fireChangeEvent();
	}
	
	
	/**
	 * Returns the status of this simulation.  This method examines whether the
	 * simulation has been outdated and returns {@link Status#OUTDATED} accordingly.
	 * 
	 * @return the status
	 * @see Status
	 */
	public Status getStatus() {
		if (status == Status.UPTODATE || status == Status.LOADED) {
			if (rocket.getFunctionalModID() != simulatedRocketID ||
					!conditions.equals(simulatedConditions))
				return Status.OUTDATED;
		}
		
		return status;
	}
	
	


	public void simulate(SimulationListener... additionalListeners)
						throws SimulationException {
		
		if (this.status == Status.EXTERNAL) {
			throw new SimulationException("Cannot simulate imported simulation.");
		}
		
		SimulationEngine simulator;
		
		try {
			simulator = simulationEngineClass.newInstance();
		} catch (InstantiationException e) {
			throw new IllegalStateException("Cannot instantiate simulator.", e);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("Cannot access simulator instance?! BUG!", e);
		} catch (NullPointerException e) {
			throw new IllegalStateException("Simulator null", e);
		}
		
		SimulationConditions simulationConditions = conditions.toSimulationConditions();
		for (SimulationListener l : additionalListeners) {
			simulationConditions.getSimulationListenerList().add(l);
		}
		
		for (String className : simulationListeners) {
			SimulationListener l = null;
			try {
				Class<?> c = Class.forName(className);
				l = (SimulationListener) c.newInstance();
			} catch (Exception e) {
				throw new SimulationListenerException("Could not instantiate listener of " +
						"class: " + className, e);
			}
			simulationConditions.getSimulationListenerList().add(l);
		}
		
		long t1, t2;
		log.debug("Simulation: calling simulator");
		t1 = System.currentTimeMillis();
		simulatedData = simulator.simulate(simulationConditions);
		t2 = System.currentTimeMillis();
		log.debug("Simulation: returning from simulator, simulation took " + (t2 - t1) + "ms");
		
		// Set simulated info after simulation, will not be set in case of exception
		simulatedConditions = conditions.clone();
		simulatedMotors = getConfiguration().getMotorConfigurationDescription();
		simulatedRocketID = rocket.getFunctionalModID();
		
		status = Status.UPTODATE;
		fireChangeEvent();
	}
	
	
	/**
	 * Return the conditions used in the previous simulation, or <code>null</code>
	 * if this simulation has not been run.
	 * 
	 * @return	the conditions used in the previous simulation, or <code>null</code>.
	 */
	public GUISimulationConditions getSimulatedConditions() {
		return simulatedConditions;
	}
	
	/**
	 * Return the warnings generated in the previous simulation, or
	 * <code>null</code> if this simulation has not been run.  This is the same
	 * warning set as contained in the <code>FlightData</code> object.
	 * 
	 * @return	the warnings during the previous simulation, or <code>null</code>.
	 * @see		FlightData#getWarningSet()
	 */
	public WarningSet getSimulatedWarnings() {
		if (simulatedData == null)
			return null;
		return simulatedData.getWarningSet();
	}
	
	
	/**
	 * Return a string describing the motor configuration of the previous simulation,
	 * or <code>null</code> if this simulation has not been run.
	 * 
	 * @return	a description of the motor configuration of the previous simulation, or
	 * 			<code>null</code>.
	 * @see		Rocket#getMotorConfigurationNameOrDescription(String)
	 */
	public String getSimulatedMotorDescription() {
		return simulatedMotors;
	}
	
	/**
	 * Return the flight data of the previous simulation, or <code>null</code> if
	 * this simulation has not been run.
	 * 
	 * @return	the flight data of the previous simulation, or <code>null</code>.
	 */
	public FlightData getSimulatedData() {
		return simulatedData;
	}
	
	

	/**
	 * Returns a copy of this simulation suitable for cut/copy/paste operations.  
	 * This excludes any simulated data.
	 *  
	 * @return	a copy of this simulation and its conditions.
	 */
	@SuppressWarnings("unchecked")
	public Simulation copy() {
		try {
			
			Simulation copy = (Simulation) super.clone();
			
			copy.status = Status.NOT_SIMULATED;
			copy.conditions = this.conditions.clone();
			copy.simulationListeners = (ArrayList<String>) this.simulationListeners.clone();
			copy.listeners = new ArrayList<ChangeListener>();
			copy.simulatedConditions = null;
			copy.simulatedMotors = null;
			copy.simulatedData = null;
			copy.simulatedRocketID = -1;
			
			return copy;
			

		} catch (CloneNotSupportedException e) {
			throw new BugException("Clone not supported, BUG", e);
		}
	}
	
	
	/**
	 * Create a duplicate of this simulation with the specified rocket.  The new
	 * simulation is in non-simulated state.
	 * 
	 * @param newRocket		the rocket for the new simulation.
	 * @return				a new simulation with the same conditions and properties.
	 */
	@SuppressWarnings("unchecked")
	public Simulation duplicateSimulation(Rocket newRocket) {
		Simulation copy = new Simulation(newRocket);
		
		copy.name = this.name;
		copy.conditions.copyFrom(this.conditions);
		copy.simulationListeners = (ArrayList<String>) this.simulationListeners.clone();
		copy.simulationStepperClass = this.simulationStepperClass;
		copy.aerodynamicCalculatorClass = this.aerodynamicCalculatorClass;
		
		return copy;
	}
	
	

	@Override
	public void addChangeListener(ChangeListener listener) {
		listeners.add(listener);
	}
	
	@Override
	public void removeChangeListener(ChangeListener listener) {
		listeners.remove(listener);
	}
	
	protected void fireChangeEvent() {
		ChangeListener[] ls = listeners.toArray(new ChangeListener[0]);
		ChangeEvent e = new ChangeEvent(this);
		for (ChangeListener l : ls) {
			l.stateChanged(e);
		}
	}
	
	


	private class ConditionListener implements ChangeListener {
		
		private Status oldStatus = null;
		
		@Override
		public void stateChanged(ChangeEvent e) {
			if (getStatus() != oldStatus) {
				oldStatus = getStatus();
				fireChangeEvent();
			}
		}
	}
}
