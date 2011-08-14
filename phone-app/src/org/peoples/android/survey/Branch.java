/*---------------------------------------------------------------------------*
 * Branch.java                                                               *
 *                                                                           *
 * Model for a survey branch. Can be evaluated to true/false to determine    *
 * what question to display next.  Contains a set of conditions.             *
 *---------------------------------------------------------------------------*/
package org.peoples.android.survey;

import java.util.Collection;
import java.util.Map;

/**
 * Model for a survey branch.
 *
 * @author Diego Vargas
 * @author Austin Walker
 **/
public class Branch
{
	//The next question to go to if this branch is true
	private final int q_id;
	private Question next_q;
	
	//Set of Conditions that belong to this branch; starts empty
	private final Collection<Condition> conditions;
	
	/*-----------------------------------------------------------------------*/

	/**
	 * Create a new Branch.
	 * 
	 * @param q_id - the id of the next question to go to
	 * @param c - {@link Condition}s for this Branch
	 * 
	 * @see setQuestion
	 */
	public Branch(int q_id, Collection<Condition> c)
	{
		next_q = null;
		this.q_id = q_id;
		conditions = c;
	}
	
	/**
	 * Set the Branch's {@link Question}.  Needed to avoid infinite recursion
	 * in {@link Survey#Survey(int, android.content.Context)}. Should only be
	 * called once.
	 * 
	 * @param qMap - a mapping of Question objects to their ids
	 * 
	 * @throws RuntimeException if called multiple times
	 * @throws RuntimeException if the qMap doesn't have the needed Question
	 */
	public void setQuestion(Map<Integer, Question> qMap)
	{
		if (next_q != null) throw new RuntimeException(
					"attempt to set condition question multiple times");
		if (!qMap.containsKey(q_id)) throw new RuntimeException(
				"bad question map");
		next_q = qMap.get(q_id);
	}
	
	/**
	 * Evaluate this Branch.
	 * 
	 * @return true or false
	 * 
	 * @throws RuntimeException if called before {@link #setQuestion}
	 */
	public boolean eval()
	{
		if (next_q == null) throw new RuntimeException(
				"must set question before evaluating");
			
		boolean status = true;
		for (Condition c : conditions)
		{
			if (c.eval() == false)
				status = false;
		}
		return status;
	}
	
	/**
	 * Get the Question this Branch points to
	 * 
	 * @return {@link Question} this Branch points to
	 */
	public Question nextQuestion()
	{
		return next_q;
	}
}
