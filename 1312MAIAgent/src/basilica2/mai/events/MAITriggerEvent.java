package basilica2.mai.events;

import edu.cmu.cs.lti.basilica2.core.Component;
import edu.cmu.cs.lti.basilica2.core.Event;

/**
 * MAITriggerEvent
 * 
 * Fired by RuleBasedTriggerComputer when a trigger condition is met.
 * PlanExecutor listens for these events and executes corresponding plan steps.
 */
public class MAITriggerEvent extends Event {
    
    private String triggerName;
    private double priority;
    
    public MAITriggerEvent(Component source, String triggerName, double priority) {
        super(source);
        this.triggerName = triggerName; 
        this.priority = priority;
    }
    
    public String getTriggerName() {
        return triggerName;
    }
    
    public double getPriority() {
        return priority;
    }
    
    @Override
    public String getName() {
        return "MAI_TRIGGER_" + triggerName;
    }

    @Override
    public String toString() {
        return "MAITriggerEvent{name=" + getName() + ", priority=" + priority + "}";
    }
}
