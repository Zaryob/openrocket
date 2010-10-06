package net.sf.openrocket.optimization.rocketoptimization;

import net.sf.openrocket.document.Simulation;
import net.sf.openrocket.unit.UnitGroup;
import net.sf.openrocket.util.ChangeSource;

/**
 * An interface what modifies a single parameter in a rocket simulation
 * based on a double value in the range [0...1].
 * 
 * @author Sampo Niskanen <sampo.niskanen@iki.fi>
 */
public interface SimulationModifier extends ChangeSource {
	
	/**
	 * Return a name describing this modifier.
	 * @return	a name describing this modifier.
	 */
	public String getName();
	
	
	/**
	 * Return the object this modifier is related to.  This is for example the
	 * rocket component this modifier is modifying.  This object can be used by a
	 * UI to group related modifiers.
	 * 
	 * @return	the object this modifier is related to, or <code>null</code>.
	 */
	public Object getRelatedObject();
	
	
	/**
	 * Return the current value of the modifier in SI units.
	 * @return	the current value of this parameter in SI units.
	 */
	public double getCurrentValue();
	
	
	/**
	 * Return the minimum value (corresponding to scaled value 0) in SI units.
	 * @return	the value corresponding to scaled value 0.
	 */
	public double getMinValue();
	
	/**
	 * Set the minimum value (corresponding to scaled value 0) in SI units.
	 * @param value	the value corresponding to scaled value 0.
	 */
	public void setMinValue(double value);
	
	
	/**
	 * Return the maximum value (corresponding to scaled value 1) in SI units.
	 * @return	the value corresponding to scaled value 1.
	 */
	public double getMaxValue();
	
	/**
	 * Set the maximum value (corresponding to scaled value 1) in SI units.
	 * @param value	the value corresponding to scaled value 1.
	 */
	public void setMaxValue(double value);
	
	
	/**
	 * Return the unit group used for the values returned by {@link #getCurrentValue()} etc.
	 * @return	the unit group
	 */
	public UnitGroup getUnitGroup();
	
	
	/**
	 * Return the current scaled value.  This is normally within the range [0...1], but
	 * can be outside the range if the current value is outside of the min and max values.
	 * @return
	 */
	public double getCurrentScaledValue();
	
	

	/**
	 * Modify the specified simulation to the corresponding parameter value.
	 * 
	 * @param simulation	the simulation to modify
	 * @param scaledValue	the scaled value in the range [0...1]
	 */
	public void modify(Simulation simulation, double scaledValue);
	
}
