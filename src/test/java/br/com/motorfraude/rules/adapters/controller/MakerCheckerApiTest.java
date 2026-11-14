package br.com.motorfraude.rules.adapters.controller;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MakerCheckerApiTest extends RestTestSupport {

    @Test
    @DisplayName("H6.1: o autor ativa a própria regra: 403 em Problem Details, e nada é publicado")
    void autorRecebe403() throws Exception {
        createRule();

        mvc.perform(asAuthor(postJson("/v1/rules/R07/versions/1:activate", "")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:motorfraude:problem:self-approval"))
                .andExpect(jsonPath("$.status").value(403));
        org.assertj.core.api.Assertions.assertThat(fixture.ruleSets.all()).isEmpty();
    }

    @Test
    @DisplayName("H6.2: outra pessoa ativa e o conjunto sai com ela como quem publicou")
    void outraPessoaAtiva() throws Exception {
        createRule();

        mvc.perform(asApprover(postJson("/v1/rules/R07/versions/1:activate", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishedBy").value("coordenador"));
    }

    @Test
    @DisplayName("ativa voltando para a sombra pela API: 200 com a regra SHADOW")
    void ativaVoltaParaSombra() throws Exception {
        createRule();
        mvc.perform(asApprover(postJson("/v1/rules/R07/versions/1:activate", ""))).andExpect(status().isOk());

        mvc.perform(asAuthor(postJson("/v1/rules/R07/versions/1:shadow", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.rules[0].status").value("SHADOW"));
    }
}
