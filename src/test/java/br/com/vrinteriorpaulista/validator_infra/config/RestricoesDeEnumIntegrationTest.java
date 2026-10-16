package br.com.vrinteriorpaulista.validator_infra.config;

import br.com.vrinteriorpaulista.validator_infra.entity.Usuario;
import br.com.vrinteriorpaulista.validator_infra.enums.Perfil;
import br.com.vrinteriorpaulista.validator_infra.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Banco de produção criado antes dos perfis novos: a restrição antiga recusa DIRETORIA até o ajuste rodar.
 */
@IntegrationTest
class RestricoesDeEnumIntegrationTest {

    @Autowired
    private RestricoesDeEnum restricoes;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UsuarioRepository usuarioRepo;

    @Test
    void atualizaRestricaoDesatualizadaComOsValoresDoEnum() {
        simularBancoAntigo();
        assertThatThrownBy(() -> usuarioRepo.save(usuario(Perfil.DIRETORIA)))
                .isInstanceOf(DataIntegrityViolationException.class);

        restricoes.run(new DefaultApplicationArguments());

        Usuario salvo = usuarioRepo.save(usuario(Perfil.DIRETORIA));
        assertThat(salvo.getId()).isNotNull();
        assertThat(definicao()).contains("'DIRETORIA'", "'SUPORTE'", "'PROJETOS'");
    }

    @Test
    void valorQueSaiuDoEnumMantemARestricaoAnteriorSemDerrubarNada() {
        restricoes.run(new DefaultApplicationArguments());
        String antes = definicao();

        restricoes.sincronizar("", "usuario", "perfil", java.util.List.of("SO_ESTE"));

        assertThat(definicao()).isEqualTo(antes);
    }

    private void simularBancoAntigo() {
        jdbc.execute("alter table usuario drop constraint if exists usuario_perfil_check");
        jdbc.execute("alter table usuario add constraint usuario_perfil_check check (perfil in ('ADMIN', 'COMERCIAL', 'TECNICO'))");
    }

    private String definicao() {
        return jdbc.queryForObject(
                "select pg_get_constraintdef(oid) from pg_constraint where conname = 'usuario_perfil_check'", String.class);
    }

    private static Usuario usuario(Perfil perfil) {
        String login = "enum." + UUID.randomUUID().toString().substring(0, 8);
        Usuario u = new Usuario();
        u.setLogin(login);
        u.setSenha("$2a$10$naoimporta");
        u.setNome("Teste " + perfil);
        u.setEmail(login + "@vr.com.br");
        u.setPerfil(perfil);
        u.setAtivo(true);
        return u;
    }
}
