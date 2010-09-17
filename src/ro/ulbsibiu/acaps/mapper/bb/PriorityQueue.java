package ro.ulbsibiu.acaps.mapper.bb;

import org.apache.log4j.Logger;

import ro.ulbsibiu.acaps.mapper.util.MathUtils;

/**
 * The Priority Queue used with the Branch-and-Bound algorithm. It is used for
 * prioritizing the mapping nodes.
 * 
 * @see MappingNode
 * 
 * @author cipi
 * 
 */
class PriorityQueue {
	
	/**
	 * Logger for this class
	 */
	private static final Logger logger = Logger.getLogger(PriorityQueue.class);

	/** the length of the queue */
	private int length;

	/** the head element */
	private MappingNode head;

	/**
	 * Default constructor
	 */
	public PriorityQueue() {
		length = 0;
		head = null;
	}

	/**
	 * @return the length of this queue
	 */
	public int length() {
		if (logger.isTraceEnabled()) {
			logger.trace("priority queue lenngth is " + length);
		}
		return length;
	}

	/**
	 * @return whether or not this queue is empty
	 */
	public boolean empty() {
		boolean empty = false;

		if (length == 0) {
			empty = true;
		}

		if (logger.isTraceEnabled()) {
			logger.trace(empty == true ? "Priority queue is empty"
					: "Priority queue is not empty");
		}
		
		return empty;
	}

	/**
	 * Inserts a {@link MappingNode} into this priority queue
	 * 
	 * @param node
	 *            the mapping node
	 */
	public void insert(MappingNode node) {
		// here we should insert the node at the position which
		// is decided by the cost of the node
		if (logger.isDebugEnabled()) {
			logger.debug("Inserting node " + node.getId());
		}
		if (length == 0) {
			head = node;
			node.next = null;
			length++;
			return;
		}
		MappingNode parentNode = null;
		MappingNode curNode = head;
		for (int i = 0; i < length; i++) {
			if (MathUtils.definitelyGreaterThan(curNode.cost, node.cost)) {
				break;
			}
			parentNode = curNode;
			curNode = curNode.next;
		}
		if (parentNode == null) {
			MappingNode oldHead = head;
			head = node;
			node.next = oldHead;
			length++;
			return;
		}
		MappingNode pNode = parentNode.next;
		parentNode.next = node;
		node.next = pNode;
		length++;
	}

	/**
	 * Removes the first element from this queue.
	 * 
	 * @return the removed mapping node
	 */
	public MappingNode next() {
		if (length == 0)
			return null;
		MappingNode oldHead = head;
		head = oldHead.next;
		length--;
		if (logger.isDebugEnabled()) {
			logger.debug("Removing node " + oldHead.getId());
		}
		return oldHead;
	}

}
