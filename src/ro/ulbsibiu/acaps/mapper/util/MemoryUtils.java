package ro.ulbsibiu.acaps.mapper.util;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

/**
 * Utility class for measuring memory consumption.
 * 
 * @author cipi
 * 
 */
public class MemoryUtils {

	private MemoryUtils() {
		;
	}

	/**
	 * Retrieves the amount of heap memory consumed by the JVM when this method
	 * was invoked.
	 * <p>
	 * The Java virtual machine has a heap that is the runtime data area from
	 * which memory for all class instances and arrays are allocated. It is
	 * created at the Java virtual machine start-up. Heap memory for objects is
	 * reclaimed by an automatic memory management system which is known as a
	 * garbage collector. The heap may be of a fixed size or may be expanded and
	 * shrunk. The memory for the heap does not need to be contiguous.
	 * </p>
	 * 
	 * @return the used heap memory
	 */
	public static long getUsedHeapMemory() {
		MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
		MemoryUsage heap = memBean.getHeapMemoryUsage();

		return heap.getUsed();
	}

	/**
	 * Retrieves the amount of non heap memory consumed by the JVM when this
	 * method was invoked.
	 * <p>
	 * The Java virtual machine manages memory other than the heap (referred as
	 * non-heap memory). The Java virtual machine has a method area that is
	 * shared among all threads. The method area belongs to non-heap memory. It
	 * stores per-class structures such as a runtime constant pool, field and
	 * method data, and the code for methods and constructors. It is created at
	 * the Java virtual machine start-up. The method area is logically part of
	 * the heap but a Java virtual machine implementation may choose not to
	 * either garbage collect or compact it. Similar to the heap, the method
	 * area may be of a fixed size or may be expanded and shrunk. The memory for
	 * the method area does not need to be contiguous. In addition to the method
	 * area, a Java virtual machine implementation may require memory for
	 * internal processing or optimization which also belongs to non-heap
	 * memory. For example, the JIT compiler requires memory for storing the
	 * native machine code translated from the Java virtual machine code for
	 * high performance.
	 * </p>
	 * 
	 * @return the used non heap memory
	 */
	public static long getUsedNonHeapMemory() {
		MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
		MemoryUsage nonHeap = memBean.getNonHeapMemoryUsage();

		return nonHeap.getUsed();
	}

	/**
	 * @return the used heap + the used non heap
	 */
	public static long getUsedMemory() {
		return getUsedHeapMemory() + getUsedNonHeapMemory();
	}

	/**
	 * Retrieves the maximum amount of heap memory available for the JVM when
	 * this method was invoked.
	 * <p>
	 * The Java virtual machine has a heap that is the runtime data area from
	 * which memory for all class instances and arrays are allocated. It is
	 * created at the Java virtual machine start-up. Heap memory for objects is
	 * reclaimed by an automatic memory management system which is known as a
	 * garbage collector. The heap may be of a fixed size or may be expanded and
	 * shrunk. The memory for the heap does not need to be contiguous.
	 * </p>
	 * 
	 * @return the used heap memory
	 */
	public static long getMaxHeapMemory() {
		MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
		MemoryUsage heap = memBean.getHeapMemoryUsage();

		return heap.getMax();
	}
	
	/**
	 * Retrieves the maximum amount of non heap memory available for the JVM when
	 * this method was invoked.
	 * <p>
	 * The Java virtual machine manages memory other than the heap (referred as
	 * non-heap memory). The Java virtual machine has a method area that is
	 * shared among all threads. The method area belongs to non-heap memory. It
	 * stores per-class structures such as a runtime constant pool, field and
	 * method data, and the code for methods and constructors. It is created at
	 * the Java virtual machine start-up. The method area is logically part of
	 * the heap but a Java virtual machine implementation may choose not to
	 * either garbage collect or compact it. Similar to the heap, the method
	 * area may be of a fixed size or may be expanded and shrunk. The memory for
	 * the method area does not need to be contiguous. In addition to the method
	 * area, a Java virtual machine implementation may require memory for
	 * internal processing or optimization which also belongs to non-heap
	 * memory. For example, the JIT compiler requires memory for storing the
	 * native machine code translated from the Java virtual machine code for
	 * high performance.
	 * </p>
	 * 
	 * @return the used non heap memory
	 */
	public static long getMaxNonHeapMemory() {
		MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
		MemoryUsage nonHeap = memBean.getNonHeapMemoryUsage();

		return nonHeap.getMax();
	}
	
	/**
	 * @return the max heap + the max non heap
	 */
	public static long getMaxMemory() {
		return getMaxHeapMemory() + getMaxNonHeapMemory();
	}

	
	/**
	 * Retrieves the amount of heap memory available for the JVM when
	 * this method was invoked.
	 * <p>
	 * The Java virtual machine has a heap that is the runtime data area from
	 * which memory for all class instances and arrays are allocated. It is
	 * created at the Java virtual machine start-up. Heap memory for objects is
	 * reclaimed by an automatic memory management system which is known as a
	 * garbage collector. The heap may be of a fixed size or may be expanded and
	 * shrunk. The memory for the heap does not need to be contiguous.
	 * </p>
	 * 
	 * @return the used heap memory
	 */
	public static long getCommitedHeapMemory() {
		MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
		MemoryUsage heap = memBean.getHeapMemoryUsage();

		return heap.getCommitted();
	}
	
	/**
	 * Retrieves the amount of non heap memory available for the JVM when
	 * this method was invoked.
	 * <p>
	 * The Java virtual machine manages memory other than the heap (referred as
	 * non-heap memory). The Java virtual machine has a method area that is
	 * shared among all threads. The method area belongs to non-heap memory. It
	 * stores per-class structures such as a runtime constant pool, field and
	 * method data, and the code for methods and constructors. It is created at
	 * the Java virtual machine start-up. The method area is logically part of
	 * the heap but a Java virtual machine implementation may choose not to
	 * either garbage collect or compact it. Similar to the heap, the method
	 * area may be of a fixed size or may be expanded and shrunk. The memory for
	 * the method area does not need to be contiguous. In addition to the method
	 * area, a Java virtual machine implementation may require memory for
	 * internal processing or optimization which also belongs to non-heap
	 * memory. For example, the JIT compiler requires memory for storing the
	 * native machine code translated from the Java virtual machine code for
	 * high performance.
	 * </p>
	 * 
	 * @return the used non heap memory
	 */
	public static long getCommitedNonHeapMemory() {
		MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
		MemoryUsage nonHeap = memBean.getNonHeapMemoryUsage();

		return nonHeap.getCommitted();
	}
	
	/**
	 * @return the committed heap + the committed non heap
	 */
	public static long getCommitedMemory() {
		return getCommitedHeapMemory() + getCommitedNonHeapMemory();
	}
	
	public static void main(String[] args) {
		long memoryStart = MemoryUtils.getUsedHeapMemory();
		int[] a = new int[100];
		for (int i = 0; i < a.length; i++) {
			// System.out.println(a[i]);
		}
		long memoryEnd = MemoryUtils.getUsedHeapMemory();
		System.out.println("Used heap memory: " + (memoryEnd - memoryStart)
				+ " Bytes");
	}

}
