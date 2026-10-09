package br.com.vrinteriorpaulista.validator_infra.service;

import br.com.vrinteriorpaulista.validator_infra.dto.response.ResultadoAnaliseDTO;
import br.com.vrinteriorpaulista.validator_infra.dto.response.ResultadoAnaliseDTO.ItemAvaliadoDTO;
import br.com.vrinteriorpaulista.validator_infra.dto.vision.AnaliseEquipamentoDTO;
import br.com.vrinteriorpaulista.validator_infra.dto.vision.ArmazenamentoExtraido;
import br.com.vrinteriorpaulista.validator_infra.dto.vision.CampoExtraido;
import br.com.vrinteriorpaulista.validator_infra.dto.vision.CpuExtraida;
import br.com.vrinteriorpaulista.validator_infra.dto.vision.MemoriaExtraida;
import br.com.vrinteriorpaulista.validator_infra.dto.vision.SistemaOperacionalExtraido;
import br.com.vrinteriorpaulista.validator_infra.entity.EquipamentoHomologado;
import br.com.vrinteriorpaulista.validator_infra.entity.Requisito;
import br.com.vrinteriorpaulista.validator_infra.enums.CategoriaEquipamento;
import br.com.vrinteriorpaulista.validator_infra.enums.FuncaoEquipamento;
import br.com.vrinteriorpaulista.validator_infra.enums.StatusAnalise;
import br.com.vrinteriorpaulista.validator_infra.enums.StatusHomologacao;
import br.com.vrinteriorpaulista.validator_infra.repository.EquipamentoHomologadoRepository;
import br.com.vrinteriorpaulista.validator_infra.repository.RequisitoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MotorRegrasServiceTest {

    private static final CategoriaEquipamento CATEGORIA = CategoriaEquipamento.SERVIDOR;
    private static final FuncaoEquipamento FUNCAO = FuncaoEquipamento.BANCO_DADOS;

    @Mock
    private RequisitoRepository requisitoRepo;

    @Mock
    private EquipamentoHomologadoRepository homologadoRepo;

    private MotorRegrasService motor;

    @BeforeEach
    void setUp() {
        motor = new MotorRegrasService(requisitoRepo, homologadoRepo);
    }

    @Nested
    class SemRequisitoCadastrado {

        @Test
        void deveRetornarRequerAnaliseQuandoNaoHouverRequisitoCadastrado() {
            dadoRequisitos();

            ResultadoAnaliseDTO resultado = avaliar(equipamentoCompleto());

            assertThat(resultado.resultado()).isEqualTo(StatusAnalise.REQUER_ANALISE);
            assertThat(resultado.itens()).isEmpty();
            assertThat(resultado.justificativa())
                    .isEqualTo("Nenhum requisito cadastrado para esta categoria/função");
        }

        @Test
        void deveRetornarRequerAnaliseParaCategoriaEFuncaoOutro() {
            when(requisitoRepo.findByCategoriaAndFuncaoAndVigenteTrue(
                    CategoriaEquipamento.OUTRO, FuncaoEquipamento.OUTRO)).thenReturn(List.of());

            ResultadoAnaliseDTO resultado = motor.avaliar(
                    equipamentoCompleto(), CategoriaEquipamento.OUTRO, FuncaoEquipamento.OUTRO);

            assertThat(resultado.resultado()).isEqualTo(StatusAnalise.REQUER_ANALISE);
        }
    }

    @Nested
    class ErroDaIa {

        @Test
        void deveRetornarRequerAnaliseQuandoIaRetornarErro() {
            AnaliseEquipamentoDTO dados = dados(null, null, null, null, "imagem_nao_reconhecida");

            ResultadoAnaliseDTO resultado = avaliar(dados);

            assertThat(resultado.resultado()).isEqualTo(StatusAnalise.REQUER_ANALISE);
            assertThat(resultado.justificativa())
                    .isEqualTo("IA não reconheceu a imagem: imagem_nao_reconhecida");
            verifyNoInteractions(requisitoRepo, homologadoRepo);
        }

        @Test
        void deveIgnorarCamposValidosQuandoIaRetornarErro() {
            AnaliseEquipamentoDTO completo = equipamentoCompleto();
            AnaliseEquipamentoDTO dados = dados(completo.cpu(), completo.memoria(),
                    completo.armazenamento(), completo.sistema_operacional(), "imagem_nao_reconhecida");

            ResultadoAnaliseDTO resultado = avaliar(dados);

            assertThat(resultado.resultado()).isEqualTo(StatusAnalise.REQUER_ANALISE);
            assertThat(resultado.itens()).isEmpty();
            verifyNoInteractions(requisitoRepo, homologadoRepo);
        }
    }

    @Nested
    class ValorNumericoInvalido {

        @Test
        void deveRetornarRequerAnaliseQuandoValorEncontradoNaoForNumerico() {
            dadoRequisitos(requisito("so_versao", ">=", "10"));
            AnaliseEquipamentoDTO dados = dados(null, null, null,
                    new SistemaOperacionalExtraido(campo("Windows 11"), campo("Pro")), null);

            ResultadoAnaliseDTO resultado = avaliar(dados);

            assertThat(resultado.resultado()).isEqualTo(StatusAnalise.REQUER_ANALISE);
            assertThat(unicoItem(resultado).observacao())
                    .isEqualTo("Valor não numérico ou mal formatado: Pro");
        }

        @Test
        void deveRetornarRequerAnaliseQuandoValorDoRequisitoForMalFormatado() {
            dadoRequisitos(requisito("ram_gb", ">=", "16GB"));

            ResultadoAnaliseDTO resultado = avaliar(equipamentoCompleto());

            assertThat(resultado.resultado()).isEqualTo(StatusAnalise.REQUER_ANALISE);
            assertThat(unicoItem(resultado).status()).isEqualTo(StatusAnalise.REQUER_ANALISE);
        }
    }

    @Nested
    class ConfiancaBaixa {

        @Test
        void deveRetornarRequerAnaliseQuandoConfiancaForMenorQueMinima() {
            dadoRequisitos(requisito("ram_gb", ">=", "16"));
            AnaliseEquipamentoDTO dados = dados(null, new MemoriaExtraida(campo(32, 0.5)), null, null, null);

            ResultadoAnaliseDTO resultado = avaliar(dados);

            assertThat(resultado.resultado()).isEqualTo(StatusAnalise.REQUER_ANALISE);
            ItemAvaliadoDTO item = unicoItem(resultado);
            assertThat(item.valorEncontrado()).isEqualTo("32");
            assertThat(item.observacao()).isEqualTo("Confiança baixa (50%) na identificação.");
        }

        @Test
        void deveConsiderarConclusivoQuandoConfiancaForExatamenteMinima() {
            dadoRequisitos(requisito("ram_gb", ">=", "16"));
            AnaliseEquipamentoDTO dados = dados(null,
                    new MemoriaExtraida(campo(32, CampoExtraido.CONFIANCA_MINIMA)), null, null, null);

            assertThat(avaliar(dados).resultado()).isEqualTo(StatusAnalise.ATENDE);
        }

        @Test
        void deveRetornarRequerAnaliseQuandoConfiancaNaoForInformada() {
            dadoRequisitos(requisito("ram_gb", ">=", "16"));
            AnaliseEquipamentoDTO dados = dados(null, new MemoriaExtraida(campo(32, null)), null, null, null);

            ResultadoAnaliseDTO resultado = avaliar(dados);

            assertThat(resultado.resultado()).isEqualTo(StatusAnalise.REQUER_ANALISE);
            assertThat(unicoItem(resultado).observacao()).isEqualTo("Confiança não informada na identificação.");
        }
    }

    @Nested
    class CampoNulo {

        @Test
        void deveRetornarRequerAnaliseQuandoGrupoDoCampoForNulo() {
            dadoRequisitos(requisito("ram_gb", ">=", "16"));
            AnaliseEquipamentoDTO dados = dados(null, null, null, null, null);

            ResultadoAnaliseDTO resultado = avaliar(dados);

            assertThat(resultado.resultado()).isEqualTo(StatusAnalise.REQUER_ANALISE);
            ItemAvaliadoDTO item = unicoItem(resultado);
            assertThat(item.valorEncontrado()).isNull();
            assertThat(item.observacao()).isEqualTo("Campo não identificado pela IA. Necessário análise manual.");
        }

        @Test
        void deveRetornarRequerAnaliseQuandoValorDoCampoForNulo() {
            dadoRequisitos(requisito("ram_gb", ">=", "16"));
            AnaliseEquipamentoDTO dados = dados(null, new MemoriaExtraida(campo(null, 0.0)), null, null, null);

            assertThat(avaliar(dados).resultado()).isEqualTo(StatusAnalise.REQUER_ANALISE);
        }

        @Test
        void deveRetornarRequerAnaliseQuandoNaoHouverDiscos() {
            dadoRequisitos(requisito("armazenamento_tipo", "==", "SSD"));
            AnaliseEquipamentoDTO dados = dados(null, null, List.of(), null, null);

            assertThat(avaliar(dados).resultado()).isEqualTo(StatusAnalise.REQUER_ANALISE);
        }
    }

    @Nested
    class TodosAtendem {

        @Test
        void deveRetornarAtendeQuandoTodosOsRequisitosForemAtendidos() {
            dadoRequisitos(
                    requisito("ram_gb", ">=", "16"),
                    requisito("cpu_cores", ">=", "4"),
                    requisito("cpu_geracao", ">=", "10"),
                    requisito("armazenamento_tipo", "==", "SSD"),
                    requisito("armazenamento_gb", ">=", "480")
            );

            ResultadoAnaliseDTO resultado = avaliar(equipamentoCompleto());

            assertThat(resultado.resultado()).isEqualTo(StatusAnalise.ATENDE);
            assertThat(resultado.justificativa()).isEqualTo("Todos os requisitos foram atendidos.");
            assertThat(resultado.itens()).hasSize(5)
                    .allMatch(i -> i.status() == StatusAnalise.ATENDE);
        }
    }

    @Nested
    class NaoAtende {

        @Test
        void deveRetornarNaoAtendeQuandoUmRequisitoForReprovado() {
            dadoRequisitos(
                    requisito("ram_gb", ">=", "32"),
                    requisito("cpu_cores", ">=", "4")
            );

            ResultadoAnaliseDTO resultado = avaliar(equipamentoCompleto());

            assertThat(resultado.resultado()).isEqualTo(StatusAnalise.NAO_ATENDE);
            assertThat(resultado.justificativa()).isEqualTo("Um ou mais requisitos não foram atendidos.");
            assertThat(resultado.itens())
                    .extracting(ItemAvaliadoDTO::campo, ItemAvaliadoDTO::status)
                    .containsExactly(
                            tuple("ram_gb", StatusAnalise.NAO_ATENDE),
                            tuple("cpu_cores", StatusAnalise.ATENDE)
                    );
        }

        @Test
        void devePriorizarNaoAtendeSobreRequerAnalise() {
            dadoRequisitos(
                    requisito("ram_gb", ">=", "32"),
                    requisito("so_versao", ">=", "10")
            );

            assertThat(avaliar(equipamentoCompleto()).resultado()).isEqualTo(StatusAnalise.NAO_ATENDE);
        }
    }

    @Nested
    class MultiplosDiscos {

        @BeforeEach
        void requisitosSsd480() {
            dadoRequisitos(
                    requisito("armazenamento_tipo", "==", "SSD"),
                    requisito("armazenamento_gb", ">=", "480")
            );
        }

        @Test
        void deveAtenderQuandoHouverHdd4tbESsd480gb() {
            AnaliseEquipamentoDTO dados = dados(null, null,
                    List.of(disco("HDD", 4000), disco("SSD", 480)), null, null);

            ResultadoAnaliseDTO resultado = avaliar(dados);

            assertThat(resultado.resultado()).isEqualTo(StatusAnalise.ATENDE);
            assertThat(resultado.itens())
                    .extracting(ItemAvaliadoDTO::valorEncontrado)
                    .containsExactly("SSD", "480");
        }

        @Test
        void naoDeveAtenderQuandoHouverApenasHdd1tb() {
            AnaliseEquipamentoDTO dados = dados(null, null, List.of(disco("HDD", 1000)), null, null);

            ResultadoAnaliseDTO resultado = avaliar(dados);

            assertThat(resultado.resultado()).isEqualTo(StatusAnalise.NAO_ATENDE);
            assertThat(resultado.itens().get(0).status()).isEqualTo(StatusAnalise.NAO_ATENDE);
            ItemAvaliadoDTO capacidade = resultado.itens().get(1);
            assertThat(capacidade.status()).isEqualTo(StatusAnalise.NAO_ATENDE);
            assertThat(capacidade.observacao())
                    .isEqualTo("Capacidade atende, mas o disco não é do tipo exigido (SSD).");
        }

        @Test
        void naoDeveAtenderQuandoSsdForPequenoEHddForGrande() {
            AnaliseEquipamentoDTO dados = dados(null, null,
                    List.of(disco("SSD", 240), disco("HDD", 2000)), null, null);

            ResultadoAnaliseDTO resultado = avaliar(dados);

            assertThat(resultado.resultado()).isEqualTo(StatusAnalise.NAO_ATENDE);
            assertThat(resultado.itens().get(0).status()).isEqualTo(StatusAnalise.ATENDE);
            ItemAvaliadoDTO capacidade = resultado.itens().get(1);
            assertThat(capacidade.status()).isEqualTo(StatusAnalise.NAO_ATENDE);
            assertThat(capacidade.valorEncontrado()).isEqualTo("240, 2000");
        }

        @Test
        void deveRequererAnaliseQuandoTipoDoDiscoGrandeForInconclusivo() {
            ArmazenamentoExtraido discoIncerto = new ArmazenamentoExtraido(campo("SSD", 0.4), campo(960));
            AnaliseEquipamentoDTO dados = dados(null, null,
                    List.of(disco("HDD", 2000), discoIncerto), null, null);

            assertThat(avaliar(dados).resultado()).isEqualTo(StatusAnalise.REQUER_ANALISE);
        }
    }

    @Nested
    class Operadores {

        @Test
        void maiorOuIgualDeveAtenderNoLimite() {
            dadoRequisitos(requisito("ram_gb", ">=", "16"));

            assertThat(avaliar(comRam(16)).resultado()).isEqualTo(StatusAnalise.ATENDE);
        }

        @Test
        void maiorOuIgualNaoDeveAtenderAbaixoDoLimite() {
            dadoRequisitos(requisito("ram_gb", ">=", "16"));

            assertThat(avaliar(comRam(8)).resultado()).isEqualTo(StatusAnalise.NAO_ATENDE);
        }

        @Test
        void menorOuIgualDeveAtenderNoLimite() {
            dadoRequisitos(requisito("ram_gb", "<=", "16"));

            assertThat(avaliar(comRam(16)).resultado()).isEqualTo(StatusAnalise.ATENDE);
        }

        @Test
        void menorOuIgualNaoDeveAtenderAcimaDoLimite() {
            dadoRequisitos(requisito("ram_gb", "<=", "16"));

            assertThat(avaliar(comRam(32)).resultado()).isEqualTo(StatusAnalise.NAO_ATENDE);
        }

        @Test
        void igualDeveIgnorarMaiusculasEEspacos() {
            dadoRequisitos(requisito("cpu_fabricante", "==", "Intel"));

            assertThat(avaliar(comCpuFabricante("  INTEL ")).resultado()).isEqualTo(StatusAnalise.ATENDE);
        }

        @Test
        void igualNaoDeveAtenderValorDiferente() {
            dadoRequisitos(requisito("cpu_fabricante", "==", "Intel"));

            assertThat(avaliar(comCpuFabricante("AMD")).resultado()).isEqualTo(StatusAnalise.NAO_ATENDE);
        }

        @Test
        void inDeveAtenderQuandoValorEstiverNaLista() {
            dadoRequisitos(requisito("cpu_fabricante", "IN", "Intel | AMD"));

            assertThat(avaliar(comCpuFabricante("amd")).resultado()).isEqualTo(StatusAnalise.ATENDE);
        }

        @Test
        void inNaoDeveAtenderQuandoValorNaoEstiverNaLista() {
            dadoRequisitos(requisito("cpu_fabricante", "IN", "Intel|AMD"));

            assertThat(avaliar(comCpuFabricante("Qualcomm")).resultado()).isEqualTo(StatusAnalise.NAO_ATENDE);
        }

        @Test
        void matchesDeveAtenderQuandoTrechoCasarComRegex() {
            dadoRequisitos(requisito("so_nome", "MATCHES", "Windows 10|Windows 11"));

            assertThat(avaliar(comSo("Microsoft windows 11 Pro")).resultado()).isEqualTo(StatusAnalise.ATENDE);
        }

        @Test
        void matchesNaoDeveAtenderQuandoTrechoNaoCasar() {
            dadoRequisitos(requisito("so_nome", "MATCHES", "Windows 10|Windows 11"));

            assertThat(avaliar(comSo("Windows 7 Professional")).resultado()).isEqualTo(StatusAnalise.NAO_ATENDE);
        }
    }

    @Nested
    class Homologacao {

        @Test
        void deveRetornarRequerAnaliseQuandoEquipamentoNaoConstarNaBase() {
            dadoRequisitos(requisito("ram_gb", ">=", "16"));
            when(homologadoRepo.findByFabricanteIgnoreCaseAndModeloIgnoreCase("dell", "optiplex 7090"))
                    .thenReturn(List.of());
            when(homologadoRepo.findByFabricanteIgnoreCaseAndModeloContainingIgnoreCase("dell", "optiplex 7090"))
                    .thenReturn(List.of());

            ResultadoAnaliseDTO resultado = avaliar(identificado("Dell", "OptiPlex 7090"));

            assertThat(resultado.resultado()).isEqualTo(StatusAnalise.REQUER_ANALISE);
            ItemAvaliadoDTO item = resultado.itens().get(0);
            assertThat(item.campo()).isEqualTo("homologacao");
            assertThat(item.valorEncontrado()).isEqualTo("Dell OptiPlex 7090");
            assertThat(item.status()).isEqualTo(StatusAnalise.REQUER_ANALISE);
            assertThat(item.observacao()).isEqualTo("Equipamento não consta na base de homologados");
        }

        @Test
        void deveAtenderQuandoEquipamentoForHomologado() {
            dadoRequisitos(requisito("ram_gb", ">=", "16"));
            dadoHomologacao(StatusHomologacao.HOMOLOGADO);

            ResultadoAnaliseDTO resultado = avaliar(identificado("dell", "optiplex 7090"));

            assertThat(resultado.resultado()).isEqualTo(StatusAnalise.ATENDE);
            assertThat(resultado.itens()).hasSize(2);
            assertThat(resultado.itens().get(0).observacao()).isEqualTo("Equipamento homologado");
        }

        @Test
        void deveRetornarNaoAtendeQuandoEquipamentoForNaoHomologado() {
            dadoRequisitos(requisito("ram_gb", ">=", "16"));
            dadoHomologacao(StatusHomologacao.NAO_HOMOLOGADO);

            ResultadoAnaliseDTO resultado = avaliar(identificado("Dell", "OptiPlex 7090"));

            assertThat(resultado.resultado()).isEqualTo(StatusAnalise.NAO_ATENDE);
            assertThat(resultado.itens().get(0).observacao()).isEqualTo("Equipamento marcado como NÃO homologado");
        }

        @Test
        void deveRetornarRequerAnaliseQuandoEquipamentoEstiverEmAvaliacao() {
            dadoRequisitos(requisito("ram_gb", ">=", "16"));
            dadoHomologacao(StatusHomologacao.EM_AVALIACAO);

            ResultadoAnaliseDTO resultado = avaliar(identificado("Dell", "OptiPlex 7090"));

            assertThat(resultado.resultado()).isEqualTo(StatusAnalise.REQUER_ANALISE);
            assertThat(resultado.itens().get(0).observacao()).isEqualTo("Equipamento em avaliação de homologação");
        }

        @Test
        void deveRetornarRequerAnaliseQuandoEquipamentoEstiverDescontinuado() {
            dadoRequisitos(requisito("ram_gb", ">=", "16"));
            dadoHomologacao(StatusHomologacao.DESCONTINUADO);

            ResultadoAnaliseDTO resultado = avaliar(identificado("Dell", "OptiPlex 7090"));

            assertThat(resultado.resultado()).isEqualTo(StatusAnalise.REQUER_ANALISE);
            assertThat(resultado.itens().get(0).observacao()).isEqualTo("Equipamento descontinuado, avaliar substituto");
        }

        @Test
        void devePularHomologacaoQuandoIaNaoTrouxerFabricante() {
            dadoRequisitos(requisito("ram_gb", ">=", "16"));

            ResultadoAnaliseDTO resultado = avaliar(identificado(null, "OptiPlex 7090"));

            assertThat(resultado.resultado()).isEqualTo(StatusAnalise.ATENDE);
            assertThat(resultado.itens()).extracting(ItemAvaliadoDTO::campo).containsExactly("ram_gb");
            verifyNoInteractions(homologadoRepo);
        }

        @Test
        void devePularHomologacaoQuandoIaNaoTrouxerModelo() {
            dadoRequisitos(requisito("ram_gb", ">=", "16"));

            ResultadoAnaliseDTO resultado = avaliar(identificado("Dell", null));

            assertThat(resultado.resultado()).isEqualTo(StatusAnalise.ATENDE);
            verifyNoInteractions(homologadoRepo);
        }

        @Test
        void deveRetornarNaoAtendeQuandoNaoHomologadoMesmoSemRequisitoCadastrado() {
            dadoRequisitos();
            dadoHomologacao(StatusHomologacao.NAO_HOMOLOGADO);

            ResultadoAnaliseDTO resultado = avaliar(identificado("Dell", "OptiPlex 7090"));

            assertThat(resultado.resultado()).isEqualTo(StatusAnalise.NAO_ATENDE);
            assertThat(resultado.itens()).hasSize(1);
        }

        @Test
        void deveIgnorarSufixoSffAoBuscarModelo() {
            dadoRequisitos(requisito("ram_gb", ">=", "16"));
            when(homologadoRepo.findByFabricanteIgnoreCaseAndModeloIgnoreCase("dell", "optiplex 7090"))
                    .thenReturn(List.of(homologado("OptiPlex 7090", StatusHomologacao.HOMOLOGADO)));

            ResultadoAnaliseDTO resultado = avaliar(identificado("Dell", "OptiPlex 7090 SFF"));

            assertThat(resultado.resultado()).isEqualTo(StatusAnalise.ATENDE);
            assertThat(resultado.itens().get(0).valorEncontrado()).isEqualTo("Dell OptiPlex 7090 SFF");
        }

        @Test
        void deveColapsarEspacosEIgnorarSufixoDeGeracaoG() {
            dadoRequisitos(requisito("ram_gb", ">=", "16"));
            when(homologadoRepo.findByFabricanteIgnoreCaseAndModeloIgnoreCase("hp", "elitedesk 800"))
                    .thenReturn(List.of(homologado("EliteDesk 800", StatusHomologacao.HOMOLOGADO)));

            ResultadoAnaliseDTO resultado = avaliar(identificado("  HP ", "EliteDesk    800  G5"));

            assertThat(resultado.resultado()).isEqualTo(StatusAnalise.ATENDE);
        }

        @Test
        void deveBuscarPorContemQuandoBuscaExataFalhar() {
            dadoRequisitos(requisito("ram_gb", ">=", "16"));
            when(homologadoRepo.findByFabricanteIgnoreCaseAndModeloContainingIgnoreCase("hp", "proliant ml30"))
                    .thenReturn(List.of(homologado("ProLiant ML30 Gen10", StatusHomologacao.HOMOLOGADO)));

            ResultadoAnaliseDTO resultado = avaliar(identificado("HP", "ProLiant ML30 Gen10"));

            assertThat(resultado.resultado()).isEqualTo(StatusAnalise.ATENDE);
            verify(homologadoRepo).findByFabricanteIgnoreCaseAndModeloIgnoreCase("hp", "proliant ml30");
        }

        @Test
        void deveRetornarRequerAnaliseQuandoModeloSoTiverSufixo() {
            dadoRequisitos(requisito("ram_gb", ">=", "16"));

            ResultadoAnaliseDTO resultado = avaliar(identificado("Dell", "Tower"));

            assertThat(resultado.resultado()).isEqualTo(StatusAnalise.REQUER_ANALISE);
            assertThat(resultado.itens().get(0).observacao()).isEqualTo("Equipamento não consta na base de homologados");
            verifyNoInteractions(homologadoRepo);
        }

        @Test
        void deveRetornarRequerAnaliseQuandoBuscaPorContemForAmbigua() {
            dadoRequisitos(requisito("ram_gb", ">=", "16"));
            when(homologadoRepo.findByFabricanteIgnoreCaseAndModeloContainingIgnoreCase("dell", "optiplex"))
                    .thenReturn(List.of(
                            homologado("OptiPlex 7090", StatusHomologacao.HOMOLOGADO),
                            homologado("OptiPlex 3020", StatusHomologacao.NAO_HOMOLOGADO)
                    ));

            ResultadoAnaliseDTO resultado = avaliar(identificado("Dell", "OptiPlex"));

            assertThat(resultado.resultado()).isEqualTo(StatusAnalise.REQUER_ANALISE);
            assertThat(resultado.itens().get(0).observacao())
                    .startsWith("Correspondência ambígua na base de homologados");
        }

        private void dadoHomologacao(StatusHomologacao status) {
            when(homologadoRepo.findByFabricanteIgnoreCaseAndModeloIgnoreCase(any(), any()))
                    .thenReturn(List.of(homologado("OptiPlex 7090", status)));
        }

        private EquipamentoHomologado homologado(String modelo, StatusHomologacao status) {
            EquipamentoHomologado homologado = new EquipamentoHomologado();
            homologado.setFabricante("Dell");
            homologado.setModelo(modelo);
            homologado.setStatus(status);
            return homologado;
        }
    }

    private void dadoRequisitos(Requisito... requisitos) {
        when(requisitoRepo.findByCategoriaAndFuncaoAndVigenteTrue(any(), any()))
                .thenReturn(List.of(requisitos));
    }

    private ResultadoAnaliseDTO avaliar(AnaliseEquipamentoDTO dados) {
        return motor.avaliar(dados, CATEGORIA, FUNCAO);
    }

    private static ItemAvaliadoDTO unicoItem(ResultadoAnaliseDTO resultado) {
        assertThat(resultado.itens()).hasSize(1);
        return resultado.itens().get(0);
    }

    private static Requisito requisito(String campo, String operador, String valor) {
        Requisito r = new Requisito();
        r.setCategoria(CATEGORIA);
        r.setFuncao(FUNCAO);
        r.setCampo(campo);
        r.setOperador(operador);
        r.setValor(valor);
        r.setVigente(true);
        return r;
    }

    private static <T> CampoExtraido<T> campo(T valor) {
        return new CampoExtraido<>(valor, 0.95);
    }

    private static <T> CampoExtraido<T> campo(T valor, Double confianca) {
        return new CampoExtraido<>(valor, confianca);
    }

    private static ArmazenamentoExtraido disco(String tipo, int capacidadeGb) {
        return new ArmazenamentoExtraido(campo(tipo), campo(capacidadeGb));
    }

    private static AnaliseEquipamentoDTO dados(CpuExtraida cpu,
                                               MemoriaExtraida memoria,
                                               List<ArmazenamentoExtraido> discos,
                                               SistemaOperacionalExtraido so,
                                               String erro) {
        return new AnaliseEquipamentoDTO(
                null, null,
                cpu, memoria, discos, so,
                0.9, List.of(), null, erro
        );
    }

    private static AnaliseEquipamentoDTO equipamentoCompleto() {
        return dados(
                new CpuExtraida(campo("Intel"), campo("Core i5-10500"), campo(10), campo(6), campo(12)),
                new MemoriaExtraida(campo(16)),
                List.of(disco("SSD", 512)),
                new SistemaOperacionalExtraido(campo("Windows 11"), campo("Pro")),
                null
        );
    }

    private static AnaliseEquipamentoDTO identificado(String fabricante, String modelo) {
        AnaliseEquipamentoDTO base = equipamentoCompleto();
        return new AnaliseEquipamentoDTO(
                fabricante != null ? campo(fabricante) : null,
                modelo != null ? campo(modelo) : null,
                base.cpu(), base.memoria(), base.armazenamento(), base.sistema_operacional(),
                0.9, List.of(), null, null
        );
    }

    private static AnaliseEquipamentoDTO comRam(int gb) {
        return dados(null, new MemoriaExtraida(campo(gb)), null, null, null);
    }

    private static AnaliseEquipamentoDTO comCpuFabricante(String fabricante) {
        return dados(new CpuExtraida(campo(fabricante), null, null, null, null), null, null, null, null);
    }

    private static AnaliseEquipamentoDTO comSo(String nome) {
        return dados(null, null, null, new SistemaOperacionalExtraido(campo(nome), null), null);
    }
}
