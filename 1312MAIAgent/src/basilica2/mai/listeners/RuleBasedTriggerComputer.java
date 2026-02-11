package basilica2.mai.listeners;

import basilica2.agents.components.InputCoordinator;
import basilica2.agents.events.MessageEvent;
import basilica2.agents.listeners.BasilicaAdapter;
import basilica2.agents.listeners.BasilicaPreProcessor;
import basilica2.mai.events.MAITriggerEvent;
import edu.cmu.cs.lti.basilica2.core.Agent;
import edu.cmu.cs.lti.basilica2.core.Event;
import edu.cmu.cs.lti.project911.utils.log.Logger;

/**
 * RuleBasedTriggerComputer for MAI Agent
 * 
 * Evaluates dialogue variable annotations and fires MAI trigger events
 * following priority order:
 * 1. Metacognitive (priority 0.80)
 * 2. Cognitive (priority 0.75)
 * 3. Shared perspective (priority 0.70)
 * 4. Behavioral (priority 0.65)
 * 5. Socio-emotional (priority 0.60)
 */
public class RuleBasedTriggerComputer extends BasilicaAdapter implements BasilicaPreProcessor {
    
    public static String GENERIC_NAME = "RuleBasedTriggerComputer";
    public static String GENERIC_TYPE = "Computer";
    
    private InputCoordinator coordinator;
    
    // Trigger configuration
    private static final String[] TRIGGER_NAMES = {
        "METACOGNITIVE", "COGNITIVE", "SHARED_PERSPECTIVE", "BEHAVIORAL", "SOCIO_EMOTIONAL"
    };
    
    private static final double[] TRIGGER_PRIORITIES = {
        0.80, 0.75, 0.70, 0.65, 0.60
    };
    
    // Blackout tracking: last time each trigger was fired
    private long[] triggerLastFired = new long[TRIGGER_NAMES.length];
    private int[] blackoutSeconds = { 180, 180, 180, 180, 180 };
    
    public RuleBasedTriggerComputer(Agent a) {
        super(a, GENERIC_NAME);
        
        // Load blackout durations from properties if available
        try {
            blackoutSeconds[0] = Integer.parseInt(getProperties().getProperty("trigger.metacognitive_blackout", "40"));
            blackoutSeconds[1] = Integer.parseInt(getProperties().getProperty("trigger.cognitive_blackout", "180"));
            blackoutSeconds[2] = Integer.parseInt(getProperties().getProperty("trigger.shared_perspective_blackout", "180"));
            blackoutSeconds[3] = Integer.parseInt(getProperties().getProperty("trigger.behavioral_blackout", "180"));
            blackoutSeconds[4] = Integer.parseInt(getProperties().getProperty("trigger.socio_emotional_blackout", "180"));
        } catch (Exception e) {
            log(Logger.LOG_WARNING, "Using default blackout durations");
        }
        
        // Initialize blackout tracking
        long now = System.currentTimeMillis();
        for (int i = 0; i < triggerLastFired.length; i++) {
            triggerLastFired[i] = now - (blackoutSeconds[i] * 1000); // Start all triggers ready
        }
        
        log(Logger.LOG_WARNING, "RuleBasedTriggerComputer initialized");
    }
    
    @Override
    public void preProcessEvent(InputCoordinator source, Event event) {
        this.coordinator = source;
        
        if (!(event instanceof MessageEvent)) return;
        
        MessageEvent me = (MessageEvent) event;
        
        // Evaluate all trigger rules
        String firedTrigger = evaluateTriggers(me);
        
        if (firedTrigger != null) {
            // Fire the trigger event
            fireTrigger(firedTrigger);
        }
    }
    
    /**
     * Evaluate all trigger rules against message annotations
     * @param me MessageEvent with dialogue variable annotations
     * @return Highest-priority trigger that fires and is not in blackout, or null
     */
    private String evaluateTriggers(MessageEvent me) {
        long now = System.currentTimeMillis();
        
        // METACOGNITIVE: IF (COO is High) AND (CONFUSION is High) AND (FCD is Medium OR High)
        if (isFiredAndNotInBlackout(0, now) && 
            hasAnnotation(me, "COO_High") && 
            hasAnnotation(me, "CONFUSION_High") && 
            (hasAnnotation(me, "FCD_Medium") || hasAnnotation(me, "FCD_High"))) {
            return "METACOGNITIVE";
        }
        
        // COGNITIVE: IF (DOM is High) AND (CONFUSION is High) AND (FCD is Medium OR High)
        if (isFiredAndNotInBlackout(1, now) && 
            hasAnnotation(me, "DOM_High") && 
            hasAnnotation(me, "CONFUSION_High") && 
            (hasAnnotation(me, "FCD_Medium") || hasAnnotation(me, "FCD_High"))) {
            return "COGNITIVE";
        }
        
        // SHARED_PERSPECTIVE: IF (DOM is High) AND (IP is High) AND (FIP is Medium OR High) AND (TIME is Medium OR High)
        if (isFiredAndNotInBlackout(2, now) && 
            hasAnnotation(me, "DOM_High") && 
            hasAnnotation(me, "IP_High") && 
            (hasAnnotation(me, "FIP_Medium") || hasAnnotation(me, "FIP_High")) &&
            (hasAnnotation(me, "TIME_Medium") || hasAnnotation(me, "TIME_High"))) {
            return "SHARED_PERSPECTIVE";
        }
        
        // BEHAVIORAL: IF (GINI is Medium OR High) AND (TIME is Medium OR High)
        if (isFiredAndNotInBlackout(3, now) && 
            (hasAnnotation(me, "GINI_Medium") || hasAnnotation(me, "GINI_High")) &&
            (hasAnnotation(me, "TIME_Medium") || hasAnnotation(me, "TIME_High"))) {
            return "BEHAVIORAL";
        }
        
        // SOCIO_EMOTIONAL: IF (NE is High) AND (FNE is High)
        if (isFiredAndNotInBlackout(4, now) && 
            hasAnnotation(me, "NE_High") && 
            hasAnnotation(me, "FNE_High")) {
            return "SOCIO_EMOTIONAL";
        }
        
        return null;
    }
    
    /**
     * Check if a trigger fires and is not in blackout
     * @param triggerIndex Index in TRIGGER_NAMES array
     * @param now Current time in milliseconds
     * @return true if trigger can fire (not in blackout)
     */
    private boolean isFiredAndNotInBlackout(int triggerIndex, long now) {
        long elapsedSeconds = (now - triggerLastFired[triggerIndex]) / 1000;
        return elapsedSeconds >= blackoutSeconds[triggerIndex];
    }
    
    /**
     * Check if a message has a specific annotation
     * @param me MessageEvent
     * @param annotationName Annotation to check (e.g., "COO_High", "CONFUSION_Medium")
     * @return true if annotation exists on message
     */
    private boolean hasAnnotation(MessageEvent me, String annotationName) {
        try {
            String[] annotations = me.checkAnnotation(annotationName);
            return annotations != null && annotations.length > 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Fire a trigger event - update blackout time
     * @param triggerName Name of trigger that fired
     */
    private void fireTrigger(String triggerName) {
        long now = System.currentTimeMillis();
        
        // Find trigger index
        int triggerIndex = -1;
        for (int i = 0; i < TRIGGER_NAMES.length; i++) {
            if (TRIGGER_NAMES[i].equals(triggerName)) {
                triggerIndex = i;
                break;
            }
        }
        
        if (triggerIndex >= 0) {
            // Update blackout time
            triggerLastFired[triggerIndex] = now;
            
            // Log the trigger
            log(Logger.LOG_WARNING, "TRIGGER FIRED: " + triggerName + 
                    " (priority: " + TRIGGER_PRIORITIES[triggerIndex] + ")");
            
            // Queue trigger event - PlanExecutor will listen for this
            // Using trigger name as the event identifier for PlanExecutor to match against plan rules
            if (coordinator != null) {
                // Create a custom event or use existing framework event
                // PlanExecutor will match this against rules in the plan XML
                MAITriggerEvent event = new MAITriggerEvent(coordinator, triggerName, TRIGGER_PRIORITIES[triggerIndex]);
                coordinator.queueNewEvent(event);
            }
        }
    }
    
    @Override
    public Class[] getPreprocessorEventClasses() {
        return new Class[] { MessageEvent.class };
    }

    @Override
    public void processEvent(InputCoordinator source, Event event) {
        // Not used in preprocessor
    }
    @Override
    public Class[] getListenerEventClasses() {
        return new Class[] { };
    }
    
}
