package ai.studyagent.factory;

import ai.studyagent.domain.StudyMaterial;

public interface StudyMaterialFactory {
    StudyMaterial createMaterial(String subjectId, String aiJsonResponse) throws IllegalArgumentException;
}