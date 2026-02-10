package basilica2.mai.listeners;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;

import basilica2.agents.components.InputCoordinator;
import basilica2.agents.components.OutputCoordinator;
import basilica2.agents.components.StateMemory;
import basilica2.agents.data.PromptTable;
import basilica2.agents.data.RollingWindow;
import basilica2.agents.events.MessageEvent;
import basilica2.agents.events.priority.BlacklistSource;
import basilica2.agents.events.priority.PriorityEvent;
import basilica2.agents.events.priority.PriorityEvent.Callback;
import basilica2.agents.listeners.BasilicaAdapter;
import basilica2.util.TimeoutAdapter;
import dadamson.words.ASentenceMatcher.SentenceMatch;
import dadamson.words.SynonymSentenceMatcher;
import edu.cmu.cs.lti.basilica2.core.Agent;
import edu.cmu.cs.lti.basilica2.core.Event;
import edu.cmu.cs.lti.project911.utils.log.Logger;
import edu.cmu.cs.lti.project911.utils.time.Timer;

//The original AbstractAccountableActor is a pedagogical engine
// does candidate detection, NLP matching, RollingWindow analysis, timing decisions, prompt generation, feedback recording
// extends BasilicaAdapter to get access to event processing and proposal system, but is not itself a listener in the traditional sense - it doesn't directly listen for events, but rather provides methods that can be called by a listener (in this case, MAIActor) when certain conditions are met.

public abstract class AbstractMAIActor extends BasilicaAdapter
{

	/*
	
	
	*/
	protected static final double WINDOW_BUFFER = 0.1;
	protected static final String AT_MOVE = "AT_MOVE";
	protected static final String SOURCE_NAME = "MAI";
	
	protected static final int HISTORY_WINDOW = 300; // define history window

	protected double feedbackWindow = 30;
	protected double candidateWindow = 10;
	protected double minimumMatch = 0.7;
	protected double blackoutTimeout = 15.0;
	protected double candidateCheckPriority = OutputCoordinator.HIGH_PRIORITY;
	protected double skipRatio = 0.0;
	protected double targetRatio = 0.5; // proportion of student moves of some
										// sort that this component wants as a
										// trigger threshold
	protected double ratioWindowTime = 60 * 5;
	protected double priorityEventTimeout = 5; 
	protected double priorityEventExpiration = 1; 
	protected int wordCountMin = 0; 
	protected int wordCountMax = -1; 
	protected boolean phraseExactMatch = false; 
	protected boolean promptAlways = false; 
	protected String topicWordPath = "accountable/topic_words.txt"; 

	protected String promptLabel;
	protected String candidateLabel;

	protected String[] productiveStudentTurns; // when detected in the check
												// window, prevent the AT move
												// from triggering
	protected String[] productiveFollowupTurns; // when detected in the check
												// window, prevent the followup
												// method from triggering.

	protected InputCoordinator source;
	protected HashMap<String, Integer> studentScores = new HashMap<String, Integer>();
	protected int teamScore = 0;

	protected PromptTable maiPromptsEn;
	protected PromptTable maiPromptsFin;
	protected String status = "";

	//protected PromptTable accountablePrompts;
	protected ArrayList<String> candidates = new ArrayList<String>();
	protected ArrayList<String> topicWords = new ArrayList<String>();
	protected SynonymSentenceMatcher sentenceMatcher;
	protected Map<String, String> slots = new HashMap<String, String>();

	protected boolean conditionActive = true;

	protected String classPath = this.getClass().getName(); 

	public AbstractMAIActor(Agent a)
	{
		// Registers this as BasilicaAdapter listener with "MAI" source name
		super(a, SOURCE_NAME);

		// Reads experiment condition (which actors are enabled)
		String condition = System.getProperty("basilica2.agents.condition", "feedback revoice agree remind social cooperate accountable_talk");
		System.err.println("AbstractMAIAgent, condition = " + condition); 
		Properties actorProperties = getProperties();

		 // Only activate if experiment condition includes this actor
		String conditionFlag = actorProperties.getProperty("condition_flag", "accountable_talk");
		conditionActive = condition.contains(conditionFlag);
		
		log(Logger.LOG_NORMAL, conditionFlag + " condition: " + conditionActive);
		
		
		 // Configure RollingWindow history size + purge interval
		RollingWindow.sharedWindow().setWindowSize(HISTORY_WINDOW, 2);

		try
		{
			// Load prompt templates
			maiPromptsEn = new PromptTable(properties.getProperty("intervention_prompts_en_file", "intervention_prompts_en.xml"));
			maiPromptsFin = new PromptTable(properties.getProperty("intervention_prompts_fin_file", "intervention_prompts_fin.xml"));
			
			// Load dictionaries
			String connPath = actorProperties.getProperty("expert_statement_file", "accountable/exemplar_statements.txt");
			String cooPath = actorProperties.getProperty("synonym_file", "accountable/synonyms.txt");
			String ipPath = actorProperties.getProperty("content_synonym_file", "accountable/content_synonyms.txt");
			String nePath = actorProperties.getProperty("stopwords_file", "stopwords.txt");
			String offtaskPath = actorProperties.getProperty("expert_statement_file", connPath);

			//topicWordPath = properties.getProperty("topic_word_file", topicWordPath);
			//loadExpertStatements(expertPath);
			//loadTopicWords(topicWordPath);

			// Setup NLP matcher and optionally enable WordNet
			//sentenceMatcher = new SynonymSentenceMatcher(contentDictionaryPath, stopwordsPath);
			//sentenceMatcher.addDictionary(dictionaryPath, false);
			//sentenceMatcher.enableWordNet(actorProperties.getProperty("use_wordnet", "false").contains("true"));

			// Load behavioral thresholds from properties
			minimumMatch = Double.parseDouble(properties.getProperty("minimum_match", "0.6"));
			feedbackWindow = Double.parseDouble(properties.getProperty("feedback_window", "60"));
			candidateWindow = Double.parseDouble(properties.getProperty("candidate_window", "10"));
			blackoutTimeout = Double.parseDouble(properties.getProperty("blackout_timeout", "15.0"));


			priorityEventTimeout = Double.parseDouble(properties.getProperty("priority_event_timeout", String.valueOf(priorityEventTimeout)));
			// System.err.println("AbstractAccountableActor, priorityEventTimeout = " + String.valueOf(priorityEventTimeout));
			priorityEventExpiration = Double.parseDouble(properties.getProperty("priority_event_expiration", String.valueOf(priorityEventExpiration)));
			wordCountMin = Integer.parseInt(properties.getProperty("word_count_min", String.valueOf(wordCountMin)));
			wordCountMax = Integer.parseInt(properties.getProperty("word_count_max", String.valueOf(wordCountMax)));
			phraseExactMatch = Boolean.valueOf(properties.getProperty("phrase_exact_match", String.valueOf(phraseExactMatch)));
			promptAlways = Boolean.valueOf(properties.getProperty("prompt_always", String.valueOf(promptAlways)));
			ratioWindowTime = Double.parseDouble(properties.getProperty("ratio_window_time", "" + ratioWindowTime));
			candidateCheckPriority = Double.parseDouble(properties.getProperty("candidate_check_priority", "" + candidateCheckPriority));
			promptLabel = actorProperties.getProperty("prompt_label", "AT_MOVE");
			candidateLabel = actorProperties.getProperty("candidate_label", "AT_CANDIDATE");
			targetRatio = Double.parseDouble(properties.getProperty("target_ratio", "" + targetRatio));
			skipRatio = Double.parseDouble(properties.getProperty("skip_ratio", "" + skipRatio));

			// Labels considered productive → suppress intervention if detected in check window
			productiveStudentTurns = actorProperties.getProperty("productive_student_turns",
					"AGREE DISAGREE CHALLENGE_CONTRIBUTION QUESTION CONTENT REVOICABLE" + candidateLabel).split("\\s");
			productiveFollowupTurns = actorProperties.getProperty("productive_followup_turns", "EXPLANATION_CONTRIBUTION CONTENT REVOICABLE" + candidateLabel)
					.split("\\s");

		}
		catch (Exception e)
		{
			System.err.println("couldn't parse MAIActor properties file:" + e.getMessage());
		}
	}

	protected void loadExpertStatements(String expertPath)
	{
		File expertFile = new File(expertPath);
		try
		{
			Scanner s = new Scanner(expertFile);
			// String statement;    				   // TEMPORARY
			while (s.hasNextLine())
			{
				candidates.add(s.nextLine());   
				// statement = s.nextLine();     		// TEMPORARY
				// candidates.add(statement);      	// TEMPORARY
				// log(Logger.LOG_NORMAL, "expert statement: " + statement);    	 // TEMPORARY
			}
		}
		catch (FileNotFoundException e)
		{
			e.printStackTrace();
		}
	}
	


	protected void loadTopicWords(String topicWordPath)
	{
		File topicWordFile = new File(topicWordPath);
		try
		{
			Scanner s = new Scanner(topicWordFile);
			// String statement;    				   // TEMPORARY
			while (s.hasNextLine())
			{
				topicWords.add(s.nextLine());   
				// statement = s.nextLine();     		// TEMPORARY
				// candidates.add(statement);      	// TEMPORARY
				// log(Logger.LOG_NORMAL, "expert statement: " + statement);    	 // TEMPORARY
			}
		}
		catch (FileNotFoundException e)
		{
			e.printStackTrace();
		}
	}
	
	

	@Override
	public void processEvent(InputCoordinator source, Event event)
	{
		// System.err.println("AbstractAccountableActor, enter processEvent"); 
		this.source = source;
		if (event instanceof MessageEvent && conditionActive)
		{
			checkForCandidate((MessageEvent) event);
		}

	}

	public String simAT(String text, int verbosity)
	{
		sentenceMatcher.setVerbosity(verbosity);
		String match = sentenceMatcher.getMatch(text, minimumMatch, candidates);
		if (match == null) return "?";
		String conceptForm = accountablePrompts.lookup(match);
		slots.put("[STUDENT]", "David");
		slots.put("[CONCEPT]", conceptForm);
		return accountablePrompts.lookup(promptLabel, slots);
	}

	protected String testAT(String text)
	{
		String match = sentenceMatcher.getMatch(text, minimumMatch, candidates);
		return match;
	}

	protected void checkForCandidate(final MessageEvent event)
	{
		if (event.hasAnnotations(candidateLabel) && shouldTriggerOnCandidate(event))
		{
			String conditionalMatch; 
			log(Logger.LOG_NORMAL, "received " + candidateLabel + " event");
			if (phraseExactMatch) {
				conditionalMatch = phraseMatch(event.getText());
			
			} else {
				conditionalMatch = sentenceMatcher.getMatch(event.getText(), minimumMatch, candidates);
				System.err.println("AbstractAccountableActor, checkForCandidate: match = " + conditionalMatch);
			}
			
			Integer wordCount = getWordCount(event.getText()); 

			if (wordCount < wordCountMin) {
				conditionalMatch = null; 
				System.err.println("AbstractAccountableAgent, classPath " + classPath + ", checkForCandidate: < wordCountMin"); 
			}	

			if ((wordCountMax != -1) && (wordCount > wordCountMax)) {
				conditionalMatch = null; 
				System.err.println("AbstractAccountableAgent, classPath " + classPath + ", checkForCandidate: > wordCountMax"); 
			}	

			final String match = conditionalMatch; 
				
			if (match != null && (getFeedbackCount(match) < 1 || promptAlways))
			// if (match != null && getFeedbackCount(match) < 10)
			{
				log(Logger.LOG_NORMAL, "either promptAlways OR no previous match for " + match);
				double sim = 1.0; 
				if (!phraseExactMatch) {
					sim = sentenceMatcher.getSentenceSimilarity(event.getText(), match);
				} 
				System.err.println("AbstractAccountableActor, checkForCandidate: sentence similarity = " + Double.toString(sim)); 
				if (promptAlways || (shouldPromptForMove(event, sim, match)))
				{
					log(Logger.LOG_NORMAL, "AT check starting...");
					PriorityEvent pete = PriorityEvent.makeBlackoutEvent(SOURCE_NAME, new Event()
					{
						{
							sender = source;
						}

						@Override
						public String getName()
						{
							return candidateLabel + " check";
						}

						@Override
						public String toString()
						{
							return "Hold-the-floor Event for " + candidateLabel + " check";
						}
					}, sim * candidateCheckPriority, priorityEventTimeout, candidateWindow);

					pete.addCallback(new Callback()
					{
						@Override
						public void accepted(PriorityEvent p)
						{
							
							log(Logger.LOG_NORMAL, "Counting to " + candidateWindow + " before checking for " + promptLabel + " opportunity");
							new Timer(candidateWindow, new TimeoutAdapter()
							{
								@Override
								public void timedOut(String id)
								{
									AbstractMAIActor.this.log(Logger.LOG_NORMAL, "checking for " + promptLabel + " opportunity");
									if (RollingWindow.sharedWindow().countAnyEvents(candidateWindow - WINDOW_BUFFER, productiveStudentTurns) < 1)
									{
										AbstractMAIActor.this.log(Logger.LOG_NORMAL, "proposing " + promptLabel + " move...");
										makeAccountableProposal(event, match);
									}
									else if (RollingWindow.sharedWindow().countAnyEvents(candidateWindow - WINDOW_BUFFER, productiveFollowupTurns) < 1)
									{
										performFollowupCheck(event);
									}
								}
							}).start();
						}

						@Override
						public void rejected(PriorityEvent p)
						{
							log(Logger.LOG_NORMAL, "AT check rejected!");

						}
					});

					// source.addProposal(pete);
					source.pushProposal(pete);
				}

			}
		}
	}

	protected void makeAccountableProposal(final MessageEvent event, String match)
	{
		makeFeedbackProposal(event.getFrom(), match, event, accountablePrompts, promptLabel, OutputCoordinator.HIGHEST_PRIORITY);
	}

	protected void makeFeedbackProposal(final String student, final String concept, final Event reference, PromptTable prompts, final String prompt,
			double priority)
	{
		String studentName = StateMemory.getSharedState(agent).getStudentName(student);
		String conceptForm = prompts.lookup(concept);
		String role = StateMemory.getSharedState(agent).getStudentRole(student); 
		slots.put("[STUDENT]", studentName);
		slots.put("[CONCEPT]", conceptForm);
		List<String> others = StateMemory.getSharedState(agent).getStudentNames();
		others.remove(student);
		if (!others.isEmpty())
			slots.put("[OTHERSTUDENT]", others.get((int) (Math.random() * others.size())));
		else
			slots.put("[OTHERSTUDENT]", "Team");
//		if (role != student) {
//			slots.put("[ROLE", "from ")
//		}

		final MessageEvent me = new MessageEvent(source, this.getAgent().getUsername(), prompts.lookup(prompt, slots), prompt, student, AT_MOVE);
		me.setReference(reference);

		final BlacklistSource blacklistSource = new BlacklistSource(SOURCE_NAME, SOURCE_NAME, ""); // block
																									// messages
																									// from
																									// ourselves
																									// and
																									// everybody
		// PriorityEvent pe = new PriorityEvent(source, me, priority, blacklistSource, 1.0);
		// PriorityEvent pe = new PriorityEvent(source, me, priority, blacklistSource, 10.0);
		PriorityEvent pe = new PriorityEvent(source, me, priority, blacklistSource, priorityEventExpiration);
		pe.setSource(SOURCE_NAME);

		source.pushProposal(pe);

		pe.addCallback(new Callback()
		{

			@Override
			public void accepted(PriorityEvent p)
			{
				recordFeedback(me, student, concept);
				recordFeedback(me, student, prompt);

				blacklistSource.setTimeout(feedbackWindow);

				if (candidates.contains(concept))
				{
					candidates.remove(concept);
				}

				new Timer(blackoutTimeout, new TimeoutAdapter() // block
																// additional AT
																// messages for
																// a while
																// longer
						{
							public void timedOut(String id)
							{
								blacklistSource.getBlacklist().remove("");
							}
						}).start();
			}

			@Override
			public void rejected(PriorityEvent p)
			{
			}
		});
	}

	protected int getFeedbackCount(String key)
	{
		return RollingWindow.sharedWindow().countEvents(HISTORY_WINDOW, key, "feedback");
	}

	// protected int getFeedbackCount(String student, String feedbackType)
	// {
	// return RollingWindow.sharedWindow().countEvents(HISTORY_WINDOW, student,
	// "feedback", feedbackType);
	// }

	protected void recordFeedback(MessageEvent me, String student, String feedbackType)
	{
		RollingWindow.sharedWindow().addEvent(me, student, feedbackType, "feedback");
		status = feedbackType + "\n" + status;
	}

	@Override
	public Class[] getListenerEventClasses()
	{
		return new Class[] { MessageEvent.class };
	}

	public void preProcessEvent(InputCoordinator source, Event event)
	{
		// System.err.println("AbstractAccountableActor, enter preProcessEvent"); 
		// boolean matchFound = false; 
		// String classPath = this.getClass().getName(); 
		System.err.println("AbstractAccountableAgent, enter preProcessEvent, classPath = " + classPath); 
		String match = null; 
		MessageEvent me = (MessageEvent) event;
		String text = me.getText();
		if (phraseExactMatch) {
			match = phraseMatch(text);
			
		} else {
			match = sentenceMatcher.getMatch(text, minimumMatch, candidates);
	
			List<SentenceMatch> matches = sentenceMatcher.getMatches(text, minimumMatch, candidates);
			// for(SentenceMatch m : matches)
			// System.out.println("match: "+m.sim + "\t" + m.matchText);
			
			System.err.println("AbstractAccountableAgent, preProcessEvent: match = " + match);
		}


		if (match != null && shouldAnnotateAsCandidate(me))
		{
			System.err.println("AbstractAccountableActor, preProcessEvent, adding candidateLabel: " + candidateLabel);
			me.addAnnotation(candidateLabel, Arrays.asList(match));
		}
	}

	/**
	 * candidate is a match - additional checks go here.
	 * 
	 * @param me
	 * @return
	 */
	public abstract boolean shouldAnnotateAsCandidate(MessageEvent me);

	/**
	 * candidate is a match - additional checks go here.
	 * 
	 * @param me
	 * @return
	 */
	public abstract boolean shouldTriggerOnCandidate(MessageEvent me);

	@Override
	public Class[] getPreprocessorEventClasses()
	{
		return new Class[] { MessageEvent.class };
	}

	public String getStatus()
	{
		return status;
	}

	public static void test(AbstractMAIActor revoicer, String file, int verbosity)
	{
		System.out.println("testing '" + file + "'...");
		try
		{
			revoicer.sentenceMatcher.setVerbosity(verbosity);
			Scanner scan = new Scanner(new File(file));

			while (scan.hasNextLine())
			{
				String next = scan.nextLine().trim();
				String match = revoicer.testAT(next);
				double sim = 0;
				if (match != null) sim = revoicer.sentenceMatcher.getSentenceSimilarity(next, match);
				System.out.println(next + "\t" + match + "\t" + sim);
			}

		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	public MessageEvent getResponseEvent(String original, String... annotations)
	{
		MessageEvent challenger = null;
		List<RollingWindow.Entry> events = RollingWindow.sharedWindow().getAnyEvents(candidateWindow - 1, annotations);
		for (RollingWindow.Entry e : events)
		{
			MessageEvent me = (MessageEvent) e.event;
			String author = me.getFrom();
			if (!author.equals(original)) challenger = me;

		}
		return challenger;
	}

	/**
	 * this AT move could have been triggered, but wasn't because the student
	 * discussion is productive. Are there other ways to provide support?
	 * 
	 * @param event
	 */
	public abstract void performFollowupCheck(final MessageEvent event);

	/**
	 * We've identified a candidate. Should we prepare to perform this move?
	 * 
	 * @param me
	 *            the candidate message
	 * @param sim
	 *            similarity to a target statement
	 * @param match
	 *            the target statement that matched
	 * @return
	 */
	private boolean shouldPromptForMove(MessageEvent me, double sim, String match)
	{
		if (Math.random() >= skipRatio)
		{
			return true;
		}
		else
		{
			log(Logger.LOG_NORMAL, "Skipping move opportunity.");
			return false;
		}
	}
	
	
	protected String phraseMatch (String text) 
	{
		text = text.toLowerCase(); 
		for(String can : candidates)
		{
			can = can.toLowerCase();
			if (text.contains(can)) 
				return text;
		}
		return null; 
	}
	
	
	protected String topicWordMatch (String text) 
	{
		System.err.println("AbstractAccountableActor, topicWordMatch: enter");
		text = text.toLowerCase(); 
		int stringIndex; 
		for(String can : topicWords)
		{	
			can = can.toLowerCase();
			stringIndex = text.indexOf(can); 
			// System.err.println("AbstractAccountableActor, topicWordMatch, word = " + can); 
			// System.err.println("AbstractAccountableActor, topicWordMatch, stringIndex = " + Integer.toString(stringIndex)); 
			// if (text.contains(can)) 
			if (stringIndex != -1) {
				System.err.println("AbstractAccountableActor, topicWordMatch: matched, returning: " + text);
				return text;
			}
		}
		System.err.println("AbstractAccountableActor, topicWordMatch: NOT matched, returning null");
		return null; 
	}

	
	protected int getWordCount(String text)
	{
		String[] wordArray = text.trim().split("\\s+");
		System.err.println("word count = " + wordArray.length);
	    return wordArray.length;
	}


	public static void main(String[] args) throws Exception
	{
		AbstractMAIActor revoicer = new RevoiceActor(null);

		// JFileChooser chooser = new JFileChooser();
		// int result = chooser.showOpenDialog(null);

		Scanner scan;
		// if(result != chooser.APPROVE_OPTION)
		scan = new Scanner(System.in);
		// else scan = new Scanner(chooser.getSelectedFile());

		String s;
		int verb = 0;

		System.out.println("enter a statement...");
		while (scan.hasNext())
		{
			s = scan.nextLine().trim();
			if (s.isEmpty()) continue;

			if (s.startsWith("t "))
			{
				test(revoicer, s.substring(2).trim(), verb);
			}

			else if (s.equals("c"))
			{
				System.out.println(revoicer.sentenceMatcher.testCandidatesForContent(revoicer.candidates, verb > 0));
			}

			else if (s.startsWith("s "))
			{
				System.out.println("Dictionary:");
				System.out.println(revoicer.sentenceMatcher.getSynonyms(s.substring(2)));
				System.out.println("Wordnet:");
				System.out.println(SynonymSentenceMatcher.getWordnetSynonyms(s.substring(2)));
			}

			else if (s.startsWith("w "))
			{
				String[] split = s.substring(2).split(" ");
				System.out.println(revoicer.sentenceMatcher.getWordSimilarity(split[0], split[1]));
			}

			else if (s.matches("(reset)|(reload)"))
			{
				revoicer = new RevoiceActor(null);
				System.out.println("reloading revoicer");

			}
			else
				try
				{
					verb = Integer.parseInt(s);
					System.out.println("verbosity set to " + verb);
				}
				catch (Exception e)
				{
					try
					{
						double thresh = Double.parseDouble(s);
						revoicer.minimumMatch = thresh;
						System.out.printf("minimum threshold set to %.3f\n", thresh);
					}
					catch (Exception ex)
					{
						System.out.println(revoicer.simAT(s, verb));
					}
				}

		}

		scan.close();

		// System.out.println(matcher.getCharacterSimilarity("v",
		// "vanderwaals"));
	}
}
