package basilica2.mai.listeners;

import basilica2.agents.components.InputCoordinator;
import basilica2.agents.components.StateMemory;
import basilica2.agents.data.RollingWindow;
import basilica2.agents.data.State;
import basilica2.agents.events.MessageEvent;
import basilica2.agents.listeners.BasilicaAdapter;
import basilica2.agents.listeners.BasilicaPreProcessor;
import edu.cmu.cs.lti.basilica2.core.Agent;
import edu.cmu.cs.lti.basilica2.core.Event;
import edu.cmu.cs.lti.project911.utils.log.Logger;

import java.util.*;


/* DialogueVariableExtractor is a new lightweight class that:

- Receives annotations from MessageAnnotator
- Applies deduction logic (DOM = ON_TASK AND NOT COO)
- Calculates frequencies (FCD, FNE, FIP, TIME, GINI)
- Adds categorization (High/Medium/Low) */

/*
    Calculates 11 dialogue variables using dictionary-based annotation:
 - COO (Coordination): Task/group coordination (from dictionary)
 - CON (Confusion): Confusion expressions (from dictionary)
 - DOM (Domain-focus): Deduced from ON_TASK AND NOT COO
 - NE (Negative Emotion): Negative emotional expressions (from dictionary)
 - IP (Individual Perspective): Personal opinions (from dictionary)
 - TIME: Elapsed time since session start
 - GINI: Participation balance coefficient
 - FCC, FCD, FNE, FIP: Frequency variables (automatic counting)
 
 Note: OFF_TASK annotation should be added by MessageAnnotator from dictionaries.
      ON_TASK is the inverse: if message is not OFF_TASK, it's ON_TASK.
 */
public class DialogueVariableExtractor extends BasilicaAdapter implements BasilicaPreProcessor {
    
    public static String GENERIC_NAME = "DialogueVariableExtractor";
    public static String GENERIC_TYPE = "Filter";
    
    private long sessionStartTime;
    private InputCoordinator coordinator;
    
    // Thresholds from properties
    private int frequencyThresholdHigh = 5;
    private int frequencyThresholdMedium = 2;
    private long timeThresholdHigh = 900; // late on the task
    private long timeThresholdMedium = 600; // in the middle of the task 
    private double giniThresholdHigh = 0.5;
    private double giniThresholdMedium = 0.25;
    
    public DialogueVariableExtractor(Agent a) {
        super(a, GENERIC_NAME);
        
        // Load thresholds from properties
        frequencyThresholdHigh = Integer.parseInt(
            getProperties().getProperty("frequency_threshold_high", "5")
        );
        frequencyThresholdMedium = Integer.parseInt(
            getProperties().getProperty("frequency_threshold_medium", "2")
        );
        timeThresholdHigh = Long.parseLong(
            getProperties().getProperty("time_threshold_high", {timeThresholdHigh}) // is that acceptable syntax?
        );
        timeThresholdMedium = Long.parseLong(
            getProperties().getProperty("time_threshold_medium", {timeThresholdMedium})
        );
        giniThresholdHigh = Double.parseDouble(
            getProperties().getProperty("gini_threshold_high", "0.5")
        );
        giniThresholdMedium = Double.parseDouble(
            getProperties().getProperty("gini_threshold_medium", "0.25")
        );
        
        sessionStartTime = System.currentTimeMillis();
        RollingWindow.sharedWindow().setWindowSize(5*60, 20); // 5-minute window
        
        log(Logger.LOG_WARNING, "DialogueVariableExtractor initialized");
    }
    
    @Override
    public void preProcessEvent(InputCoordinator source, Event event) {
        this.coordinator = source;
        
        // if there is no message then return
        if (!(event instanceof MessageEvent)) return;
        

        
        MessageEvent me = (MessageEvent) event;
        String text = me.getText();
        
        log(Logger.LOG_NORMAL, "Processing: " + me.getFrom() + " -> " + text);
        
        // Determine ON_TASK status (inverse of OFF_TASK from MessageAnnotator)
        boolean isOffTask = me.hasAnnotations("OFF_TASK");
        if (!isOffTask) {
            me.addAnnotations("ON_TASK");
        }
        
        // DEDUCTION: DOM = ON_TASK AND NOT COO
        // If message is on-task and not about coordination, it's domain-focused
        if (me.hasAnnotations("ON_TASK") && !me.hasAnnotations("COO")) {
            me.addAnnotations("DOM");
            log(Logger.LOG_NORMAL, "  -> Deduced DOM (ON_TASK and not COO)");
        }
        
        // Calculate and add derived variables
        calculateDerivedVariables(me);
    }
    
    /**
     * Calculate derived dialogue variables based on annotations and history
     */
    private void calculateDerivedVariables(MessageEvent me) {
        // FCC: Frequency of coordination+confusion in last 5 minutes
        int fcc = RollingWindow.sharedWindow().countEvents(5*60, "FCC_event");
        if (me.hasAnnotations("COO") && me.hasAnnotations("CONFUSION")) {
            fcc++;
            RollingWindow.sharedWindow().addEvent(me, "FCC_event");
        }
        me.addAnnotations("FCC_" + fcc);
        
        // FCD: Frequency of domain+confusion in last 5 minutes
        int fcd = RollingWindow.sharedWindow().countEvents(5*60, "FCD_event");
        if (me.hasAnnotations("DOM") && me.hasAnnotations("CONFUSION")) {
            fcd++;
            RollingWindow.sharedWindow().addEvent(me, "FCD_event");
        }
        me.addAnnotations("FCD_" + fcd);
        
        // FNE: Frequency of negative emotion in last 5 minutes
        int fne = RollingWindow.sharedWindow().countEvents(5*60, "NEGATIVE_EMOTION");
        me.addAnnotations("FNE_" + fne);
        
        // FIP: Frequency of individual perspective in last 5 minutes
        int fip = RollingWindow.sharedWindow().countEvents(5*60, "INDIVIDUAL_PERSPECTIVE");
        me.addAnnotations("FIP_" + fip);
        
        // TIME: Seconds since session start
        long elapsedSeconds = (System.currentTimeMillis() - sessionStartTime) / 1000;
        me.addAnnotations("TIME_" + elapsedSeconds);
        
        // GINI: Gini coefficient of participation balance
        double gini = calculateGiniCoefficient();
        me.addAnnotations("GINI_" + String.format("%.3f", gini));
        
        // Categorize all variables as High/Medium/Low for trigger matching
        categorizeVariables(me, fcc, fcd, fne, fip, elapsedSeconds, gini);
    }
    
    /**
     * Categorize continuous variable values into High/Medium/Low levels
     * Used by RuleBasedTriggerComputer for trigger rule matching
     */
    private void categorizeVariables(MessageEvent me, int fcc, int fcd, int fne, int fip, 
                                     long elapsedSeconds, double gini) {
        // Probability variables - use presence of annotation
        if (me.hasAnnotations("COO")) {
            me.addAnnotations("COO_High");
        }
        if (me.hasAnnotations("CONFUSION")) {
            me.addAnnotations("CON_High");
        }
        if (me.hasAnnotations("DOM")) {
            me.addAnnotations("DOM_High");
        }
        if (me.hasAnnotations("NEGATIVE_EMOTION")) {
            me.addAnnotations("NE_High");
        }
        if (me.hasAnnotations("INDIVIDUAL_PERSPECTIVE")) {
            me.addAnnotations("IP_High");
        }
        
        // FCC: Frequency of coordination+confusion
        if (fcc >= frequencyThresholdHigh) {
            me.addAnnotations("FCC_High");
        } else if (fcc >= frequencyThresholdMedium) {
            me.addAnnotations("FCC_Medium");
        } else {
            me.addAnnotations("FCC_Low");
        }
        
        // FCD: Frequency of domain+confusion
        if (fcd >= frequencyThresholdHigh) {
            me.addAnnotations("FCD_High");
        } else if (fcd >= frequencyThresholdMedium) {
            me.addAnnotations("FCD_Medium");
        } else {
            me.addAnnotations("FCD_Low");
        }
        
        // FNE: Frequency of negative emotion
        if (fne >= frequencyThresholdHigh) {
            me.addAnnotations("FNE_High");
        } else if (fne >= frequencyThresholdMedium) {
            me.addAnnotations("FNE_Medium");
        } else {
            me.addAnnotations("FNE_Low");
        }
        
        // FIP: Frequency of individual perspective
        if (fip >= frequencyThresholdHigh) {
            me.addAnnotations("FIP_High");
        } else if (fip >= frequencyThresholdMedium) {
            me.addAnnotations("FIP_Medium");
        } else {
            me.addAnnotations("FIP_Low");
        }
        
        // TIME: Elapsed seconds
        if (elapsedSeconds >= timeThresholdHigh) {
            me.addAnnotations("TIME_High");
        } else if (elapsedSeconds >= timeThresholdMedium) {
            me.addAnnotations("TIME_Medium");
        } else {
            me.addAnnotations("TIME_Low");
        }
        
        // GINI: Participation balance (0=equal, 1=unequal)
        if (gini >= giniThresholdHigh) {
            me.addAnnotations("GINI_High");
        } else if (gini >= giniThresholdMedium) {
            me.addAnnotations("GINI_Medium");
        } else {
            me.addAnnotations("GINI_Low");
        }
    }
    
    /**
     * Calculate Gini coefficient for participation balance
     * 0 = perfect equality, 1 = perfect inequality
     */
    private double calculateGiniCoefficient() {
        try {
            State state = StateMemory.getSharedState(agent);
            if (state == null) return 0.0;
            
            String[] studentIds = state.getStudentIds();
            if (studentIds == null || studentIds.length == 0) return 0.0;
            
            // Count messages per student
            int[] messageCounts = new int[studentIds.length];
            int totalMessages = 0;
            
            for (int i = 0; i < studentIds.length; i++) {
                messageCounts[i] = RollingWindow.sharedWindow()
                    .countEvents(Integer.MAX_VALUE, studentIds[i] + "_turn");
                totalMessages += messageCounts[i];
            }
            
            if (totalMessages == 0) return 0.0;
            
            // Calculate Gini coefficient
            // Formula: G = sum(|x_i - x_j|) / (2 * n * mean)
            double sum = 0.0;
            int n = messageCounts.length;
            double mean = totalMessages / (double) n;
            
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    sum += Math.abs(messageCounts[i] - messageCounts[j]);
                }
            }
            
            double gini = sum / (2.0 * n * n * mean);
            return Math.min(gini, 1.0); // Clamp to [0, 1]
            
        } catch (Exception e) {
            log(Logger.LOG_ERROR, "Error calculating GINI: " + e.getMessage());
            return 0.0;
        }
    }
    
    @Override
    public Class[] getPreprocessorEventClasses() {
        return new Class[] { MessageEvent.class };
    }
    
    @Override
    public Class[] getListenerEventClasses() {
        return new Class[] { };
    }
    
    @Override
    public void processEvent(InputCoordinator source, Event event) {
        // This class only acts as preprocessor, not listener
    }
    
    /**
     * Helper method to check if an annotation exists and return its level
     * Returns one of: "High", "Medium", "Low", or null if annotation doesn't exist
     */
    public static String getAnnotationLevel(MessageEvent me, String variable) {
        if (me.hasAnnotations(variable + "_High")) return "High";
        if (me.hasAnnotations(variable + "_Medium")) return "Medium";
        if (me.hasAnnotations(variable + "_Low")) return "Low";
        return null;
    }
}
