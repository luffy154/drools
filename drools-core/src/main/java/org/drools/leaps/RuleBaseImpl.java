package org.drools.leaps;

/*
 * Copyright 2006 Alexander Bagerman
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.drools.FactException;
import org.drools.RuleBase;
import org.drools.RuleIntegrationException;
import org.drools.RuleSetIntegrationException;
import org.drools.WorkingMemory;
import org.drools.rule.InvalidPatternException;
import org.drools.rule.Rule;
import org.drools.rule.RuleSet;
import org.drools.spi.FactHandleFactory;
import org.drools.spi.RuleBaseContext;

/**
 * This base class for the engine and analogous to Drool's RuleBase class. It
 * has a similar interface adapted to the Leaps algorithm
 * 
 * @author Alexander Bagerman
 * 
 */
public class RuleBaseImpl implements RuleBase {

	private HashMap leapsRules = new HashMap();

	/**
	 * TODO we do not need it here. and it references RETEoo class
	 * 
	 * The fact handle factory.
	 */
	/** The fact handle factory. */
	private final HandleFactory factHandleFactory;

	private Set ruleSets;

	private Map applicationData;

	private RuleBaseContext ruleBaseContext;

	// @todo: replace this with a weak HashSet
	/**
	 * WeakHashMap to keep references of WorkingMemories but allow them to be
	 * garbage collected
	 */
	private final transient Map workingMemories;

	/** Special value when adding to the underlying map. */
	private static final Object PRESENT = new Object();

	/**
	 * Construct.
	 * 
	 * @param rete
	 *            The rete network.
	 */
	public RuleBaseImpl() throws RuleIntegrationException,
			RuleSetIntegrationException, FactException, InvalidPatternException {
		this(new HandleFactory(), new HashSet(), new HashMap(),
				new RuleBaseContext());
	}

	/**
	 * Construct.
	 * 
	 * @param rete
	 *            The rete network.
	 * @param conflictResolver
	 *            The conflict resolver.
	 * @param factHandleFactory
	 *            The fact handle factory.
	 * @param ruleSets
	 * @param applicationData
	 */
	public RuleBaseImpl(FactHandleFactory factHandleFactory, Set ruleSets,
			Map applicationData, RuleBaseContext ruleBaseContext)
			throws RuleIntegrationException, RuleSetIntegrationException,
			FactException, InvalidPatternException {
		// because we can deal only with leaps fact handle factory
		this.factHandleFactory = (HandleFactory) factHandleFactory;
		this.ruleSets = ruleSets;
		this.applicationData = applicationData;
		this.ruleBaseContext = ruleBaseContext;
		this.workingMemories = new WeakHashMap();

		this.ruleSets = new HashSet();
		for (Iterator it = ruleSets.iterator(); it.hasNext();) {
			this.addRuleSet((RuleSet) it.next());
		}
	}

	/**
	 * @see RuleBase
	 */
	public WorkingMemory newWorkingMemory() {
		return newWorkingMemory(true);
	}

	/**
	 * @see RuleBase
	 */
	public WorkingMemory newWorkingMemory(boolean keepReference) {
		WorkingMemoryImpl workingMemory = new WorkingMemoryImpl(this);
		// add all rules added so far
		for (Iterator it = this.leapsRules.values().iterator(); it.hasNext();) {
			((WorkingMemoryImpl) workingMemory).addLeapsRules((List) it.next());
		}
		//
		if (keepReference) {
			this.workingMemories.put(workingMemory, RuleBaseImpl.PRESENT);
		}
		return workingMemory;
	}

	void disposeWorkingMemory(WorkingMemory workingMemory) {
		this.workingMemories.remove(workingMemory);
	}

	/**
	 * @see RuleBase
	 */
	public FactHandleFactory getFactHandleFactory() {
		return this.factHandleFactory;
	}

	/**
	 * returns NEW fact handle factory because each working memory needs the new
	 * one
	 * 
	 * @see RuleBase
	 */
	public FactHandleFactory newFactHandleFactory() {
		return this.factHandleFactory.newInstance();
	}

	/**
	 * @see RuleBase
	 */
	public RuleSet[] getRuleSets() {
		return (RuleSet[]) this.ruleSets.toArray(new RuleSet[this.ruleSets
				.size()]);
	}

	public Map getApplicationData() {
		return this.applicationData;
	}

	/**
	 * @see RuleBase
	 */
	public RuleBaseContext getRuleBaseContext() {
		return this.ruleBaseContext;
	}

	/**
	 * Add a <code>RuleSet</code> to the network. Iterates through the
	 * <code>RuleSet</code> adding Each individual <code>Rule</code> to the
	 * network.
	 * 
	 * @param ruleSet
	 *            The rule-set to add.
	 * 
	 * @throws RuleIntegrationException
	 *             if an error prevents complete construction of the network for
	 *             the <code>Rule</code>.
	 * @throws FactException
	 * @throws InvalidPatternException
	 */
	public void addRuleSet(RuleSet ruleSet) throws RuleIntegrationException,
			RuleSetIntegrationException, FactException, InvalidPatternException {
		Map newApplicationData = ruleSet.getApplicationData();

		// Check that the application data is valid, we cannot change the type
		// of an already declared application data variable
		for (Iterator it = newApplicationData.keySet().iterator(); it.hasNext();) {
			String identifier = (String) it.next();
			Class type = (Class) newApplicationData.get(identifier);
			if (this.applicationData.containsKey(identifier)
					&& !this.applicationData.get(identifier).equals(type)) {
				throw new RuleSetIntegrationException(ruleSet);
			}
		}
		this.applicationData.putAll(newApplicationData);

		this.ruleSets.add(ruleSet);

		Rule[] rules = ruleSet.getRules();

		for (int i = 0; i < rules.length; ++i) {
			addRule(rules[i]);
		}
	}

	/**
	 * Creates leaps rule wrappers and propagate rule to the working memories
	 * 
	 * @param rule
	 * @throws FactException
	 * @throws RuleIntegrationException
	 * @throws InvalidPatternException
	 */
	public void addRule(Rule rule) throws FactException,
			RuleIntegrationException, InvalidPatternException {
		List rules = Builder.processRule(rule);

		this.leapsRules.put(rule, rules);

		for (Iterator it = this.workingMemories.keySet().iterator(); it
				.hasNext();) {
			((WorkingMemoryImpl) it.next()).addLeapsRules(rules);
		}
	}
}
