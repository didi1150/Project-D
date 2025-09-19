package dev.core.game.voting;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class VotingSystem<T> {
    private final Map<UUID, T> votes;

    public VotingSystem() {
        this.votes = new HashMap<>();
    }

    public T castVote(UUID uuid, T voteData) {
        votes.put(uuid, voteData);
        return voteData;
    }

    public Optional<T> getVote(UUID voteId) {
        return Optional.ofNullable(votes.get(voteId));
    }

    public boolean removeVote(UUID voteId) {
        return votes.remove(voteId) != null;
    }

    public Collection<T> getAllVotes() {
        return Collections.unmodifiableCollection(votes.values());
    }

    public long countVotesFor(T target) {
        return votes.values().stream().filter(v -> v.equals(target)).count();
    }

    public int totalVotes() {
        return votes.size();
    }

}
