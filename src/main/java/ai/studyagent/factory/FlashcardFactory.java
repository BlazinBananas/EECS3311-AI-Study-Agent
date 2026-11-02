package ai.studyagent.factory;

import ai.studyagent.domain.Flashcard;
import ai.studyagent.domain.StudyMaterial;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FlashcardFactory implements StudyMaterialFactory {

    @Override
    public StudyMaterial createMaterial(String subjectId, String aiJsonResponse) throws IllegalArgumentException {
        List<Flashcard> cards = createDeck(subjectId, aiJsonResponse);
        if (cards.isEmpty()) {
            throw new IllegalArgumentException("No valid flashcards parsed from AI response.");
        }
        return cards.get(0);
    }

    public List<Flashcard> createDeck(String subjectId, String aiJsonResponse) throws IllegalArgumentException {
        try {
            List<Flashcard> deck = new ArrayList<>();
            JsonArray array = JsonParser.parseString(aiJsonResponse).getAsJsonArray();
            for (JsonElement elem : array) {
                JsonObject obj = elem.getAsJsonObject();
                String term = obj.get("term").getAsString();
                String definition = obj.get("definition").getAsString();
                deck.add(new Flashcard(UUID.randomUUID().toString(), subjectId, term, definition));
            }
            return deck;
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed Flashcard JSON from LLM", e);
        }
    }
}