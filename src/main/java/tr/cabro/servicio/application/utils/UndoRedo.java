package tr.cabro.servicio.application.utils;

import java.util.Iterator;
import java.util.Stack;
import java.util.function.Consumer;

public class UndoRedo<E> implements Iterable<E> {

    private static final int MAX_HISTORY = 20;

    private final Stack<E> stack1;
    private final Stack<E> stack2;
    private final Consumer<E> onEvict;

    public UndoRedo() {
        this(null);
    }

    /** @param onEvict geçmiş sınırı aşıldığında atılan en eski öğe için çağrılır (kaynak temizliği için). */
    public UndoRedo(Consumer<E> onEvict) {
        stack1 = new Stack<>();
        stack2 = new Stack<>();
        this.onEvict = onEvict;
    }

    public void add(E item) {
        stack1.push(item);
        stack2.clear();
        if (stack1.size() > MAX_HISTORY) {
            E evicted = stack1.remove(0);
            if (onEvict != null) {
                onEvict.accept(evicted);
            }
        }
    }

    public E undo() {
        if (stack1.size() > 1) {
            stack2.push(stack1.pop());
            return stack1.get(stack1.size() - 1);
        } else {
            return null;
        }
    }

    public E redo() {
        if (!stack2.isEmpty()) {
            E item = stack2.pop();
            stack1.push(item);
            return item;
        } else {
            return null;
        }
    }

    public E getCurrent() {
        if (stack1.isEmpty()) {
            return null;
        } else {
            return stack1.get(stack1.size() - 1);
        }
    }

    public boolean isUndoAble() {
        return stack1.size() > 1;
    }

    public boolean isRedoAble() {
        return !stack2.empty();
    }

    public void clear() {
        stack1.clear();
        stack2.clear();
    }

    /** {@link #clear()} ile aynı, ancak temizlemeden önce geçmişteki tüm öğeler için {@code onEvict} çağrılır. */
    public void clearAndClose() {
        if (onEvict != null) {
            for (E item : stack1) {
                onEvict.accept(item);
            }
            for (E item : stack2) {
                onEvict.accept(item);
            }
        }
        clear();
    }

    public void clearRedo() {
        stack2.clear();
    }

    @Override
    public Iterator<E> iterator() {
        return new MyIterator();
    }

    private class MyIterator implements Iterator<E> {

        private int index = 0;

        @Override
        public boolean hasNext() {
            if (index < stack1.size()) {
                return true;
            } else if (index < stack1.size() + stack2.size()) {
                return true;
            } else {
                return false;
            }
        }

        @Override
        public E next() {
            if (index < stack1.size()) {
                return stack1.elementAt(index++);
            } else {
                return stack2.elementAt((index++) - stack1.size());
            }
        }
    }
}
