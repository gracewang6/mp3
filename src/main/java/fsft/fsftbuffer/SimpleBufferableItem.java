package fsft.fsftbuffer;
import org.sqids.Sqids;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SimpleBufferableItem implements Bufferable {
    private static long currentIdNumber = 0;
    private static Set<String> assignedIds = new HashSet<>();
    private static final Sqids sqids = Sqids.builder().build();
    private String id;

    public SimpleBufferableItem(String id) {
        if (assignedIds.contains(id)) {
            throw new IllegalArgumentException("id already taken");
        }
        this.id = id;
        assignedIds.add(id);
    }

    public SimpleBufferableItem() {
        String sqidId = sqids.encode(List.of(currentIdNumber++));
        while (assignedIds.contains(sqidId)) {
            sqidId = sqids.encode(List.of(currentIdNumber++));
        }
        this.id = sqidId;
    }

    public String id() {
        return id;
    }
}
