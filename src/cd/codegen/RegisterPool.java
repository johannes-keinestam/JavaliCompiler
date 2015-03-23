package cd.codegen;

import java.util.Arrays;
import java.util.PriorityQueue;
import java.util.Queue;
import static cd.codegen.AssemblerHelper.*;

// Class for maintaining our register pool.
class RegisterPool {
	private Queue<String> availableRegisters = new PriorityQueue<String>();

	public RegisterPool(AstCodeGenerator astCodeGenerator) {
		availableRegisters.addAll(Arrays.asList("%eax", "%ebx", "%ecx",
				"%edx", "%edi", "%esi"));
	}

	public String reserve() {
		try {
			String reg = availableRegisters.remove();
			emitComment("Reserving register " + reg);
			return reg;
		} catch (java.util.NoSuchElementException e) {
			throw new RuntimeException("No registers left.");
		}
	}
	
	public String reserve(String reg) {
		if (isInUse(reg)) {
			throw new RuntimeException("Requested register "+reg+" already in use.");
		}
		emitComment("Reserving register " + reg);
		availableRegisters.remove(reg);
		return reg;
	}

	public void release(String reg) {
		availableRegisters.add(reg);
		emitComment("Releasing register " + reg);
	}

	protected boolean isInUse(String reg) {
		return !availableRegisters.contains(reg);
	}
}