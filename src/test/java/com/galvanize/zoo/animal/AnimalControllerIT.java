package com.galvanize.zoo.animal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.galvanize.zoo.habitat.HabitatEntity;
import com.galvanize.zoo.habitat.HabitatRepository;
import com.galvanize.zoo.habitat.HabitatType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders;
import org.springframework.restdocs.request.RequestDocumentation;
import org.springframework.test.web.servlet.MockMvc;

import javax.transaction.Transactional;
import java.util.List;


import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@AutoConfigureRestDocs(outputDir = "target/snippets")
class AnimalControllerIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AnimalRepository animalRepository;

    @Autowired
    HabitatRepository habitatRepository;

    @Test
    void create_fetchAll() throws Exception {
        AnimalDto input = new AnimalDto("monkey", AnimalType.WALKING, null, null);
        mockMvc.perform(
            post("/animals")
                .content(objectMapper.writeValueAsString(input))
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isCreated());

        mockMvc.perform(get("/animals"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("length()").value(1))
            .andExpect(jsonPath("[0].name").value("monkey"))
            .andExpect(jsonPath("[0].mood").value(Mood.UNHAPPY.name()))
            .andExpect(jsonPath("[0].type").value(AnimalType.WALKING.name()))
                .andDo(document("Animals", responseFields(
                        fieldWithPath("[0].name").description("This is the name of animal"),
                        fieldWithPath("[0].type").description("This is the type of animal"),
                        fieldWithPath("[0].mood").description("This is the mood of animal"),
                        fieldWithPath("[0].habitat").description("This is the habitat of animal")
                )));
    }

    @Test
    void create_conflict() throws Exception {
        animalRepository.save(new AnimalEntity("monkey", AnimalType.WALKING));

        AnimalDto input = new AnimalDto("monkey", AnimalType.WALKING, null, null);
        mockMvc.perform(
            post("/animals")
                .content(objectMapper.writeValueAsString(input))
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isConflict())
        .andDo(document("CreateExistingAnimal", requestFields(
                fieldWithPath("name").description("This is the name of animal"),
                fieldWithPath("type").description("This is the type of animal"),
                fieldWithPath("mood").description("This is the mood of animal"),
                fieldWithPath("habitat").description("This is the habitat of animal")
        )))
        .andDo(document("CreateExistingAnimal",responseHeaders()));
    }

    @Test
    void feed() throws Exception {
        animalRepository.save(new AnimalEntity("monkey", AnimalType.WALKING));

        mockMvc.perform(RestDocumentationRequestBuilders.post("/animals/{name}/feed", "monkey"))
            .andExpect(status().isOk())
                .andDo(document("FeedAnimal", RequestDocumentation.pathParameters(
                        parameterWithName("name").description("This is the name of animal")

                )));

        mockMvc.perform(get("/animals"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("[0].name").value("monkey"))
            .andExpect(jsonPath("[0].mood").value(Mood.HAPPY.name()));
    }

    @Test
    void move() throws Exception {
        animalRepository.save(new AnimalEntity("monkey", AnimalType.WALKING));
        habitatRepository.save(new HabitatEntity("Monkey's Jungle", HabitatType.FOREST));

        mockMvc.perform(post("/animals/monkey/move").content("Monkey's Jungle"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/animals"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("[0].name").value("monkey"))
            .andExpect(jsonPath("[0].habitat.name").value("Monkey's Jungle"));
    }

    @Test
    void move_incompatible() throws Exception {
        AnimalEntity eagle = new AnimalEntity("eagle", AnimalType.FLYING);
        eagle.setMood(Mood.HAPPY);
        animalRepository.save(eagle);
        habitatRepository.save(new HabitatEntity("Monkey's Jungle", HabitatType.FOREST));

        mockMvc.perform(post("/animals/eagle/move").content("Monkey's Jungle"))
            .andExpect(status().isConflict());

        mockMvc.perform(get("/animals"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("[0].name").value("eagle"))
            .andExpect(jsonPath("[0].mood").value(Mood.UNHAPPY.name()))
            .andExpect(jsonPath("[0].habitat").isEmpty());
    }

    @Test
    void move_occupied() throws Exception {
        animalRepository.save(new AnimalEntity("monkey", AnimalType.WALKING));

        HabitatEntity habitat = new HabitatEntity("Monkey's Jungle", HabitatType.FOREST);
        AnimalEntity chimp = new AnimalEntity("chimp", AnimalType.WALKING);
        chimp.setHabitat(habitat);
        habitat.setAnimal(chimp);
        habitatRepository.save(habitat);

        mockMvc.perform(post("/animals/monkey/move").content("Monkey's Jungle"))
            .andExpect(status().isConflict());

        mockMvc.perform(get("/animals"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("[0].name").value("monkey"))
            .andExpect(jsonPath("[0].habitat").isEmpty())
            .andExpect(jsonPath("[1].name").value("chimp"))
            .andExpect(jsonPath("[1].habitat.name").value("Monkey's Jungle"));
    }

    @Test
    void fetch_withParams() throws Exception {
        AnimalEntity monkey = new AnimalEntity("monkey", AnimalType.WALKING);
        AnimalEntity eagle = new AnimalEntity("eagle", AnimalType.FLYING);
        AnimalEntity whale = new AnimalEntity("whale", AnimalType.SWIMMING);
        AnimalEntity chimp = new AnimalEntity("chimp", AnimalType.WALKING);
        chimp.setMood(Mood.HAPPY);
        animalRepository.saveAll(List.of(monkey, eagle, whale, chimp));

        mockMvc.perform(get("/animals?mood=HAPPY&type=WALKING"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("length()").value(1))
            .andExpect(jsonPath("[0].name").value("chimp"))
            .andExpect(jsonPath("[0].mood").value(Mood.HAPPY.name()))
            .andExpect(jsonPath("[0].type").value(AnimalType.WALKING.name()));
    }
}
