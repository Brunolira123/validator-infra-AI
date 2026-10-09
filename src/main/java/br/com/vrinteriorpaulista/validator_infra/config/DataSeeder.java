package br.com.vrinteriorpaulista.validator_infra.config;

import br.com.vrinteriorpaulista.validator_infra.entity.*;
import br.com.vrinteriorpaulista.validator_infra.enums.CategoriaEquipamento;
import br.com.vrinteriorpaulista.validator_infra.enums.FuncaoEquipamento;
import br.com.vrinteriorpaulista.validator_infra.enums.Perfil;
import br.com.vrinteriorpaulista.validator_infra.enums.StatusHomologacao;
import br.com.vrinteriorpaulista.validator_infra.repository.EquipamentoHomologadoRepository;
import br.com.vrinteriorpaulista.validator_infra.repository.RequisitoRepository;
import br.com.vrinteriorpaulista.validator_infra.repository.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final RequisitoRepository requisitoRepo;
    private final EquipamentoHomologadoRepository homologadoRepo;
    private final UsuarioRepository usuarioRepo;

    public DataSeeder(RequisitoRepository requisitoRepo,
                      EquipamentoHomologadoRepository homologadoRepo,
                      UsuarioRepository usuarioRepo) {
        this.requisitoRepo = requisitoRepo;
        this.homologadoRepo = homologadoRepo;
        this.usuarioRepo = usuarioRepo;
    }

    @Override
    public void run(String... args) {
        popularUsuarioInicial();

        if (requisitoRepo.count() == 0) {
            log.info("Populando base de requisitos VR...");
            popularRequisitos();
        }
        if (homologadoRepo.count() == 0) {
            log.info("Populando base de equipamentos homologados...");
            popularHomologados();
        }
    }

    private void popularRequisitos() {
        // ===== SERVIDOR — BANCO DE DADOS =====
        criarRequisito(CategoriaEquipamento.SERVIDOR, FuncaoEquipamento.BANCO_DADOS,
                "ram_gb", ">=", "16", null,
                "RAM mínima para servidor de banco de dados");

        criarRequisito(CategoriaEquipamento.SERVIDOR, FuncaoEquipamento.BANCO_DADOS,
                "cpu_cores", ">=", "4", null,
                "Mínimo 4 cores para banco de dados");

        criarRequisito(CategoriaEquipamento.SERVIDOR, FuncaoEquipamento.BANCO_DADOS,
                "cpu_geracao", ">=", "10", null,
                "CPU de 10ª geração ou superior");

        criarRequisito(CategoriaEquipamento.SERVIDOR, FuncaoEquipamento.BANCO_DADOS,
                "armazenamento_tipo", "==", "SSD", null,
                "Armazenamento deve ser SSD");

        criarRequisito(CategoriaEquipamento.SERVIDOR, FuncaoEquipamento.BANCO_DADOS,
                "armazenamento_gb", ">=", "480", null,
                "Mínimo 480GB de armazenamento");

        // ===== SERVIDOR — APLICAÇÃO =====
        criarRequisito(CategoriaEquipamento.SERVIDOR, FuncaoEquipamento.APLICACAO,
                "ram_gb", ">=", "16", null,
                "RAM mínima para servidor de aplicação");

        criarRequisito(CategoriaEquipamento.SERVIDOR, FuncaoEquipamento.APLICACAO,
                "cpu_cores", ">=", "4", null,
                "Mínimo 4 cores para aplicação");

        criarRequisito(CategoriaEquipamento.SERVIDOR, FuncaoEquipamento.APLICACAO,
                "cpu_geracao", ">=", "10", null,
                "CPU de 10ª geração ou superior");

        criarRequisito(CategoriaEquipamento.SERVIDOR, FuncaoEquipamento.APLICACAO,
                "armazenamento_tipo", "==", "SSD", null,
                "Armazenamento deve ser SSD");

        // ===== SERVIDOR — SERVICE MANAGER =====
        criarRequisito(CategoriaEquipamento.SERVIDOR, FuncaoEquipamento.SERVICE_MANAGER,
                "ram_gb", ">=", "8", null,
                "RAM mínima para Service Manager");

        criarRequisito(CategoriaEquipamento.SERVIDOR, FuncaoEquipamento.SERVICE_MANAGER,
                "cpu_cores", ">=", "2", null,
                "Mínimo 2 cores");

        // ===== PDV =====
        criarRequisito(CategoriaEquipamento.PDV, FuncaoEquipamento.PDV,
                "ram_gb", ">=", "8", null,
                "RAM mínima para PDV");

        criarRequisito(CategoriaEquipamento.PDV, FuncaoEquipamento.PDV,
                "cpu_cores", ">=", "2", null,
                "Mínimo 2 cores para PDV");

        criarRequisito(CategoriaEquipamento.PDV, FuncaoEquipamento.PDV,
                "armazenamento_tipo", "==", "SSD", null,
                "PDV deve ter SSD");

        criarRequisito(CategoriaEquipamento.PDV, FuncaoEquipamento.PDV,
                "so_nome", "MATCHES", "Windows 10|Windows 11", null,
                "Windows 10 ou superior");

        // ===== RETAGUARDA =====
        criarRequisito(CategoriaEquipamento.RETAGUARDA, FuncaoEquipamento.RETAGUARDA,
                "ram_gb", ">=", "8", null,
                "RAM mínima para retaguarda");

        criarRequisito(CategoriaEquipamento.RETAGUARDA, FuncaoEquipamento.RETAGUARDA,
                "cpu_cores", ">=", "2", null,
                "Mínimo 2 cores");

        criarRequisito(CategoriaEquipamento.RETAGUARDA, FuncaoEquipamento.RETAGUARDA,
                "armazenamento_tipo", "==", "SSD", null,
                "Retaguarda deve ter SSD");

        // ===== CONSULTA DE PREÇO =====
        criarRequisito(CategoriaEquipamento.CONSULTA_PRECO, FuncaoEquipamento.CONSULTA_PRECO,
                "ram_gb", ">=", "4", null,
                "RAM mínima para consulta de preço");

        criarRequisito(CategoriaEquipamento.CONSULTA_PRECO, FuncaoEquipamento.CONSULTA_PRECO,
                "cpu_cores", ">=", "2", null,
                "Mínimo 2 cores");

        log.info("Base de requisitos populada: {} regras", requisitoRepo.count());
    }

    private void criarRequisito(CategoriaEquipamento categoria,
                                FuncaoEquipamento funcao,
                                String campo,
                                String operador,
                                String valor,
                                String condicaoExtra,
                                String descricao) {
        Requisito r = new Requisito();
        r.setCategoria(categoria);
        r.setFuncao(funcao);
        r.setCampo(campo);
        r.setOperador(operador);
        r.setValor(valor);
        r.setCondicaoExtra(condicaoExtra);
        r.setVersao(Requisito.VERSAO_VIGENTE);
        r.setVigente(true);
        r.setVigenteDesde(LocalDateTime.now());
        r.setDescricao(descricao);
        requisitoRepo.save(r);
    }

    private void popularHomologados() {
        // Dell
        criarHomologado("Dell", "PowerEdge T140", CategoriaEquipamento.SERVIDOR,
                "Torre", "Windows Server 2019/2022, Linux",
                StatusHomologacao.HOMOLOGADO,
                "Servidor de entrada homologado",
                "https://docs.vrsoft.com.br/vrmaster/equipamentos-homologados");

        criarHomologado("Dell", "PowerEdge T340", CategoriaEquipamento.SERVIDOR,
                "Torre", "Windows Server 2019/2022, Linux",
                StatusHomologacao.HOMOLOGADO,
                "Servidor de médio porte homologado",
                "https://docs.vrsoft.com.br/vrmaster/equipamentos-homologados");

        criarHomologado("Dell", "OptiPlex 7090", CategoriaEquipamento.PDV,
                "SFF", "Windows 10/11",
                StatusHomologacao.HOMOLOGADO,
                "Desktop homologado para PDV",
                "https://docs.vrsoft.com.br/vrmaster/equipamentos-homologados");

        // Lenovo
        criarHomologado("Lenovo", "ThinkSystem ST250", CategoriaEquipamento.SERVIDOR,
                "Torre", "Windows Server 2019/2022, Linux",
                StatusHomologacao.HOMOLOGADO,
                "Servidor de entrada homologado",
                "https://docs.vrsoft.com.br/vrmaster/equipamentos-homologados");

        criarHomologado("Lenovo", "ThinkCentre M720", CategoriaEquipamento.PDV,
                "SFF", "Windows 10/11",
                StatusHomologacao.HOMOLOGADO,
                "Desktop homologado para PDV",
                "https://docs.vrsoft.com.br/vrmaster/equipamentos-homologados");

        // HP
        criarHomologado("HP", "ProLiant ML30 Gen10", CategoriaEquipamento.SERVIDOR,
                "Torre", "Windows Server 2019/2022, Linux",
                StatusHomologacao.HOMOLOGADO,
                "Servidor de entrada homologado",
                "https://docs.vrsoft.com.br/vrmaster/equipamentos-homologados");

        log.info("Base de homologados populada: {} equipamentos", homologadoRepo.count());
    }

    private void criarHomologado(String fabricante, String modelo,
                                 CategoriaEquipamento categoria,
                                 String tipo, String so,
                                 StatusHomologacao status,
                                 String observacoes,
                                 String fonte) {
        EquipamentoHomologado e = new EquipamentoHomologado();
        e.setFabricante(fabricante);
        e.setModelo(modelo);
        e.setCategoria(categoria);
        e.setTipo(tipo);
        e.setSistemaOperacional(so);
        e.setStatus(status);
        e.setObservacoes(observacoes);
        e.setFonte(fonte);
        homologadoRepo.save(e);
    }

    private void popularUsuarioInicial() {
        if (usuarioRepo.count() == 0) {
            Usuario u = new Usuario();
            u.setLogin("admin");
            u.setSenha("admin");  // TODO: hash BCrypt quando implementar Spring Security
            u.setNome("Administrador");
            u.setEmail("admin@vr.com.br");
            u.setPerfil(Perfil.ADMIN);
            u.setAtivo(true);
            usuarioRepo.save(u);
            log.info("Usuário inicial criado: admin/admin");
        }
    }
}
