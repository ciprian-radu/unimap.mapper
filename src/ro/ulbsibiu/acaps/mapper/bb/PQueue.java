package ro.ulbsibiu.acaps.mapper.bb;

/**
 * @author cipi
 * 
 */
class PQueue {

	private int length;

	private MappingNode head;

	private int minCost;

	private int minLowerBound;

	private int minUpperBound;

	public PQueue() {
		length = 0;
		head = null;
	}

	public int length() {
		return length;
	}

	public boolean empty() {
		boolean empty = false;

		if (length == 0) {
			empty = true;
		}

		return empty;
	}

	public void insert(MappingNode node) {
		// here we should insert the node at the position which
		// is decided by the cost of the node
		if (length == 0) {
			head = node;
			node.next = null;
			length++;
			return;
		}
		MappingNode parentNode = null;
		MappingNode curNode = head;
		int i = 0;
		for (i = 0; i < length; i++) {
			if (curNode.cost > node.cost) {
				// if (curNode->upperBound > node->upperBound)
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

	public MappingNode next() {
		if (length == 0)
			return null;
		MappingNode oldHead = head;
		head = oldHead.next;
		length--;
		return oldHead;
	}

}
