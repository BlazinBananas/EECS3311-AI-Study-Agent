package ai.studyagent.factory;

import ai.studyagent.domain.Quiz;
import ai.studyagent.domain.QuizQuestion;
import ai.studyagent.domain.StudyMaterial;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class QuizFactory implements StudyMaterialFactory {

    @Override
    public StudyMaterial createMaterial(String subjectId, String aiJsonResponse) throws IllegalArgumentException {
        try {
            JsonObject root = JsonParser.parseString(aiJsonResponse).getAsJsonObject();
            String difficulty = root.has("difficulty") ? root.get("difficulty").getAsString() : "Intermediate";
            JsonArray qArray = root.getAsJsonArray("questions");

            List<QuizQuestion> questions = new ArrayList<>();
            for (JsonElement elem : qArray) {
                JsonObject qObj = elem.getAsJsonObject();
                String prompt = qObj.get("prompt").getAsString();
                int correctIndex = qObj.get("correctAnswerIndex").getAsInt();
                List<String> options = new ArrayList<>();
                for (JsonElement opt : qObj.getAsJsonArray("options")) {
                    options.add(opt.getAsString());
                }
                questions.add(new QuizQuestion(prompt, options, correctIndex));
            }
            if (questions.isEmpty()) {
                throw new IllegalArgumentException("Quiz contains zero questions.");
            }
            return new Quiz(UUID.randomUUID().toString(), subjectId, difficulty, questions);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed Quiz JSON from LLM", e);
        }
    }
}