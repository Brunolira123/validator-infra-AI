package br.com.vrinteriorpaulista.validator_infra.service;

import br.com.vrinteriorpaulista.validator_infra.dto.request.RevisaoAnaliseRequest;
import br.com.vrinteriorpaulista.validator_infra.dto.response.ResultadoAnaliseDTO;
import br.com.vrinteriorpaulista.validator_infra.dto.vision.AnaliseEquipamentoDTO;
import br.com.vrinteriorpaulista.validator_infra.dto.vision.ArmazenamentoExtraido;
import br.com.vrinteriorpaulista.validator_infra.dto.vision.CampoExtraido;
import br.com.vrinteriorpaulista.validator_infra.dto.vision.CpuExtraida;
import br.com.vrinteriorpaulista.validator_infra.dto.vision.MemoriaExtraida;
import br.com.vrinteriorpaulista.validator_infra.dto.vision.SistemaOperacionalExtraido;
import br.com.vrinteriorpaulista.validator_infra.entity.*;
import br.com.vrinteriorpaulista.validator_infra.enums.CategoriaEquipamento;
import br.com.vrinteriorpaulista.validator_infra.enums.FuncaoEquipamento;
import br.com.vrinteriorpaulista.validator_infra.repository.AnaliseRepository;
import br.com.vrinteriorpaulista.validator_infra.repository.EquipamentoRepository;
import br.com.vrinteriorpaulista.validator_infra.repository.FotoRepository;
import br.com.vrinteriorpaulista.validator_infra.repository.UsuarioRepository;
import br.com.vrinteriorpaulista.validator_infra.service.vision.VisionProvider;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
public class AnaliseService {

    private static final Map<String, String> EXTENSOES_PERMITIDAS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );
    private static final int TAMANHO_MAXIMO_NOME_ORIGINAL = 200;

    private final EquipamentoRepository equipamentoRepo;
    private final FotoRepository fotoRepo;
    private final AnaliseRepository analiseRepo;
    private final UsuarioRepository usuarioRepo;
    private final VisionProvider visionProvider;
    private final MotorRegrasService motorRegras;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transacaoLeitura;
    private final TransactionTemplate transacaoEscrita;

    @Value("${app.storage.fotos-path:./storage/fotos}")
    private String fotosPath;

    public AnaliseService(EquipamentoRepository equipamentoRepo,
                          FotoRepository fotoRepo,
                          AnaliseRepository analiseRepo,
                          UsuarioRepository usuarioRepo,
                          VisionProvider visionProvider,
                          MotorRegrasService motorRegras,
                          ObjectMapper objectMapper,
                          PlatformTransactionManager transactionManager) {
        this.equipamentoRepo = equipamentoRepo;
        this.fotoRepo = fotoRepo;
        this.analiseRepo = analiseRepo;
        this.usuarioRepo = usuarioRepo;
        this.visionProvider = visionProvider;
        this.motorRegras = motorRegras;
        this.objectMapper = objectMapper;
        this.transacaoLeitura = new TransactionTemplate(transactionManager);
        this.transacaoLeitura.setReadOnly(true);
        this.transacaoEscrita = new TransactionTemplate(transactionManager);
    }

    /**
     * Faz upload de foto e vincula ao equipamento.
     * O nome do arquivo em disco é gerado (UUID + extensão do content-type); nada do nome original entra no path.
     */
    @Transactional
    public Foto uploadFoto(Long equipamentoId, Long usuarioId, MultipartFile file) {
        String contentType = normalizarContentType(file.getContentType());
        String extensao = EXTENSOES_PERMITIDAS.get(contentType);
        if (extensao == null) {
            throw new IllegalArgumentException("Tipo de arquivo não suportado");
        }
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Arquivo vazio");
        }

        Equipamento eq = buscarEquipamento(equipamentoId);
        Usuario user = buscarUsuario(usuarioId);

        Path destino = salvarArquivo(equipamentoId, extensao, file);

        try {
            int seq = fotoRepo.findByEquipamentoIdOrderBySequenciaAsc(equipamentoId).size() + 1;
            Foto foto = new Foto();
            foto.setEquipamento(eq);
            foto.setUsuario(user);
            foto.setCaminho(destino.toString());
            foto.setNomeOriginal(sanitizarNomeOriginal(file.getOriginalFilename()));
            foto.setContentType(contentType);
            foto.setTamanhoBytes(file.getSize());
            foto.setSequencia(seq);
            return fotoRepo.save(foto);
        } catch (RuntimeException e) {
            apagarArquivo(destino);
            throw e;
        }
    }

    private Path salvarArquivo(Long equipamentoId, String extensao, MultipartFile file) {
        Path dir = Paths.get(fotosPath, String.valueOf(equipamentoId));
        Path destino = dir.resolve(UUID.randomUUID() + extensao);
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(dir);
            Files.copy(in, destino);
            return destino;
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível salvar a foto do equipamento " + equipamentoId, e);
        }
    }

    private void apagarArquivo(Path arquivo) {
        try {
            Files.deleteIfExists(arquivo);
        } catch (IOException e) {
            log.warn("Não foi possível apagar o arquivo órfão {}", arquivo, e);
        }
    }

    /**
     * Analisa o equipamento em três etapas, para não segurar conexão com o banco durante a chamada à IA:
     * 1) transação de leitura: valida entidades e carrega a última foto;
     * 2) sem transação: IA + motor de regras;
     * 3) transação de escrita: persiste a análise e atualiza o status do equipamento.
     */
    public ResultadoAnaliseDTO analisarEquipamento(Long equipamentoId, Long usuarioId) {
        DadosParaAnalise entrada = transacaoLeitura.execute(status -> carregarDadosParaAnalise(equipamentoId, usuarioId));

        AnaliseEquipamentoDTO dadosIa = visionProvider.analisarImagem(entrada.imagem(), entrada.contentType());
        ResultadoAnaliseDTO resultado = motorRegras.avaliar(dadosIa, entrada.categoria(), entrada.funcao());

        transacaoEscrita.executeWithoutResult(status -> persistirAnalise(equipamentoId, usuarioId, dadosIa, resultado));

        return resultado;
    }

    /**
     * Aplica as correções do revisor sobre a análise existente e reavalia com o motor de regras.
     * Campos revisados entram com confiança 1.0 (confirmados por humano).
     */
    @Transactional
    public ResultadoAnaliseDTO revisarAnalise(Long equipamentoId, RevisaoAnaliseRequest req) {
        Equipamento eq = buscarEquipamento(equipamentoId);
        Analise analise = analiseRepo.findByEquipamentoId(equipamentoId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Análise não encontrada. Execute a análise inicial primeiro."));
        Usuario revisor = buscarUsuario(req.usuarioId());

        aplicarRevisao(analise, req);

        AnaliseEquipamentoDTO corrigido = montarDadosCorrigidos(analise);
        ResultadoAnaliseDTO resultado = motorRegras.avaliar(corrigido, eq.getCategoria(), eq.getFuncao());

        analise.setJsonIaCorrigido(objectMapper.writeValueAsString(corrigido));
        analise.setResultado(resultado.resultado());
        analise.setJustificativa(resultado.justificativa());
        analise.setAnalisadoPor(revisor);
        analise.setAnalisadoEm(LocalDateTime.now());
        analise.setVersaoRequisitos(Requisito.VERSAO_VIGENTE);
        analiseRepo.save(analise);

        eq.setStatus(resultado.resultado());
        equipamentoRepo.save(eq);

        return resultado;
    }

    private void aplicarRevisao(Analise analise, RevisaoAnaliseRequest req) {
        if (req.fabricante() != null) analise.setFabricante(req.fabricante());
        if (req.modelo() != null) analise.setModelo(req.modelo());
        if (req.cpuFabricante() != null) analise.setCpuFabricante(req.cpuFabricante());
        if (req.cpuModelo() != null) analise.setCpuModelo(req.cpuModelo());
        if (req.cpuGeracao() != null) analise.setCpuGeracao(req.cpuGeracao());
        if (req.cpuCores() != null) analise.setCpuCores(req.cpuCores());
        if (req.cpuThreads() != null) analise.setCpuThreads(req.cpuThreads());
        if (req.ramGb() != null) analise.setRamGb(req.ramGb());
        if (req.armazenamentoTipo() != null) analise.setArmazenamentoTipo(req.armazenamentoTipo());
        if (req.armazenamentoGb() != null) analise.setArmazenamentoGb(req.armazenamentoGb());
        if (req.soNome() != null) analise.setSoNome(req.soNome());
        if (req.soVersao() != null) analise.setSoVersao(req.soVersao());
        if (req.observacoes() != null) analise.setObservacoes(req.observacoes());
    }

    private AnaliseEquipamentoDTO montarDadosCorrigidos(Analise a) {
        List<ArmazenamentoExtraido> discos = a.getArmazenamentoTipo() == null && a.getArmazenamentoGb() == null
                ? List.of()
                : List.of(new ArmazenamentoExtraido(confirmado(a.getArmazenamentoTipo()), confirmado(a.getArmazenamentoGb())));

        return new AnaliseEquipamentoDTO(
                confirmado(a.getFabricante()),
                confirmado(a.getModelo()),
                new CpuExtraida(
                        confirmado(a.getCpuFabricante()),
                        confirmado(a.getCpuModelo()),
                        confirmado(a.getCpuGeracao()),
                        confirmado(a.getCpuCores()),
                        confirmado(a.getCpuThreads())
                ),
                new MemoriaExtraida(confirmado(a.getRamGb())),
                discos,
                new SistemaOperacionalExtraido(confirmado(a.getSoNome()), confirmado(a.getSoVersao())),
                1.0,
                List.of(),
                a.getObservacoes(),
                null
        );
    }

    private <T> CampoExtraido<T> confirmado(T valor) {
        return valor == null ? null : new CampoExtraido<>(valor, 1.0);
    }

    private DadosParaAnalise carregarDadosParaAnalise(Long equipamentoId, Long usuarioId) {
        Equipamento eq = buscarEquipamento(equipamentoId);
        buscarUsuario(usuarioId);

        var fotos = fotoRepo.findByEquipamentoIdOrderBySequenciaAsc(equipamentoId);
        if (fotos.isEmpty()) {
            throw new IllegalStateException("Equipamento não possui fotos para análise");
        }
        Foto ultimaFoto = fotos.get(fotos.size() - 1);

        try {
            byte[] bytes = Files.readAllBytes(Paths.get(ultimaFoto.getCaminho()));
            return new DadosParaAnalise(bytes, ultimaFoto.getContentType(), eq.getCategoria(), eq.getFuncao());
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler a foto " + ultimaFoto.getId(), e);
        }
    }

    private void persistirAnalise(Long equipamentoId,
                                  Long usuarioId,
                                  AnaliseEquipamentoDTO dadosIa,
                                  ResultadoAnaliseDTO resultado) {
        Equipamento eq = buscarEquipamento(equipamentoId);
        Usuario user = buscarUsuario(usuarioId);

        Analise analise = analiseRepo.findByEquipamentoId(equipamentoId).orElse(new Analise());
        analise.setEquipamento(eq);
        analise.setFabricante(texto(dadosIa.fabricante()));
        analise.setModelo(texto(dadosIa.modelo()));
        if (dadosIa.cpu() != null) {
            analise.setCpuFabricante(texto(dadosIa.cpu().fabricante()));
            analise.setCpuModelo(texto(dadosIa.cpu().modelo()));
            analise.setCpuGeracao(inteiro(dadosIa.cpu().geracao()));
            analise.setCpuCores(inteiro(dadosIa.cpu().cores()));
            analise.setCpuThreads(inteiro(dadosIa.cpu().threads()));
        }
        if (dadosIa.memoria() != null) {
            analise.setRamGb(inteiro(dadosIa.memoria().total_gb()));
        }
        if (dadosIa.sistema_operacional() != null) {
            analise.setSoNome(texto(dadosIa.sistema_operacional().nome()));
            analise.setSoVersao(texto(dadosIa.sistema_operacional().versao()));
        }
        ArmazenamentoExtraido disco = discoPrincipal(dadosIa);
        if (disco != null) {
            analise.setArmazenamentoTipo(texto(disco.tipo()));
            analise.setArmazenamentoGb(inteiro(disco.capacidade_gb()));
        }
        analise.setConfiancaGlobal(dadosIa.confianca_global());
        analise.setJsonIa(objectMapper.writeValueAsString(dadosIa));
        analise.setJsonIaCorrigido(null);
        analise.setResultado(resultado.resultado());
        analise.setJustificativa(resultado.justificativa());
        analise.setAnalisadoPor(user);
        analise.setAnalisadoEm(LocalDateTime.now());
        analise.setVersaoRequisitos(Requisito.VERSAO_VIGENTE);
        analiseRepo.save(analise);

        eq.setStatus(resultado.resultado());
        equipamentoRepo.save(eq);
    }

    private Equipamento buscarEquipamento(Long id) {
        return equipamentoRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Equipamento não encontrado"));
    }

    private Usuario buscarUsuario(Long id) {
        return usuarioRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));
    }

    private String normalizarContentType(String contentType) {
        if (contentType == null) return null;
        return contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
    }

    private String sanitizarNomeOriginal(String nome) {
        if (nome == null || nome.isBlank()) return null;
        int ultimaBarra = Math.max(nome.lastIndexOf('/'), nome.lastIndexOf('\\'));
        String base = nome.substring(ultimaBarra + 1).replaceAll("\\p{Cntrl}", "").trim();
        return base.length() > TAMANHO_MAXIMO_NOME_ORIGINAL
                ? base.substring(0, TAMANHO_MAXIMO_NOME_ORIGINAL)
                : base;
    }

    private ArmazenamentoExtraido discoPrincipal(AnaliseEquipamentoDTO dados) {
        if (dados.armazenamento() == null) return null;
        return dados.armazenamento().stream()
                .filter(Objects::nonNull)
                .max(Comparator.comparingInt(d -> {
                    Integer gb = inteiro(d.capacidade_gb());
                    return gb != null ? gb : -1;
                }))
                .orElse(null);
    }

    private String texto(CampoExtraido<String> c) { return c != null ? c.valor() : null; }
    private Integer inteiro(CampoExtraido<Integer> c) { return c != null ? c.valor() : null; }

    private record DadosParaAnalise(byte[] imagem,
                                    String contentType,
                                    CategoriaEquipamento categoria,
                                    FuncaoEquipamento funcao) {}
}
