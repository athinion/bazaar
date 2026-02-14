package basilica2.mai.listeners;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

//import basilica2.accountable.listeners.AbstractAccountableActor;
import basilica2.agents.components.StateMemory;
import basilica2.agents.data.RollingWindow;
import basilica2.agents.events.MessageEvent;
import edu.cmu.cs.lti.basilica2.core.Agent;
import edu.cmu.cs.lti.project911.utils.log.Logger;

/**
 * Metacognitive Actor
 * 
 * Triggers when: (COO is High) AND (CON is High) AND (FCD is Medium OR FCD is High)
 * Goal: Prompt students to reflect on their approach and monitor understanding
 * 
 * Prompts are chosen randomly, with constraint that the same prompt
 * is not repeated on consecutive triggers.
 */
public class MetacognitiveActor extends AbstractAccountableActor {

	private String lastPromptId = null;
	private Random random = new Random();
	private List<String> availablePrompts = new ArrayList<>();

	public MetacognitiveActor(Agent a) {
		super(a);
		promptLabel = "METACOGNITIVE";
		loadAvailablePrompts();
	}

	private void loadAvailablePrompts() {
		// All prompts come from intervention_prompts.xml with ID "METACOGNITIVE"
		// The PromptTable handles selecting random text variants
		availablePrompts.add("METACOGNITIVE");
	}
 
	/**
	 * Trigger when: COO >= 0.6 AND CON >= 0.6 AND (FCD >= 2 OR FCD >= 3)
	 * (where FCD=2 is Medium, FCD=3+ is High)
	 */
	@Override
	public boolean shouldTriggerOnCandidate(MessageEvent me) {
		StateMemory memory = agent.getStateMemory();
		if (memory == null) return false;

		Double coo = toDouble(memory.getAttribute("COO"));
		Double con = toDouble(memory.getAttribute("CON"));
		Double fcd = toDouble(memory.getAttribute("FCD"));

		boolean triggered = (coo >= 0.6) && (con >= 0.6) && (fcd >= 2.0);

		if (triggered) {
			log(Logger.LOG_NORMAL,
				String.format("MetacognitiveActor trigger: COO=%.2f, CON=%.2f, FCD=%.0f", coo, con, fcd));
		}

		return triggered;
	}

	/**
	 * Get a random prompt ID, ensuring we don't repeat the last one.
	 */
	@Override
	public void performFollowupCheck(final MessageEvent event) {
		// Use this method to actually fire the intervention
		String promptId = getNextPromptId();
		makeFeedbackProposal(event.getFrom(), "", event, accountablePrompts, promptId, 0.5);
	}

	private String getNextPromptId() {
		String selected;
		do {
			selected = availablePrompts.get(random.nextInt(availablePrompts.size()));
		} while (selected.equals(lastPromptId) && availablePrompts.size() > 1);

		lastPromptId = selected;
		return selected;
	}

	private double toDouble(Object value) {
		if (value instanceof Double) return (Double) value;
		if (value instanceof Integer) return ((Integer) value).doubleValue();
		if (value instanceof String) {
			try {
				return Double.parseDouble((String) value);
			} catch (NumberFormatException e) {
				return 0.0;
			}
		}
		return 0.0;
	}

    @Override
    public void shouldAnnotateAsCandidate(MessageEvent me) {
        // TODO Auto-generated method stub
        
    }
}
