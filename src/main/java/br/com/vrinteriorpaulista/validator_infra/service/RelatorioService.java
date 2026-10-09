package br.com.vrinteriorpaulista.validator_infra.service;

import br.com.vrinteriorpaulista.validator_infra.dto.response.RelatorioLevantamentoDTO;
import br.com.vrinteriorpaulista.validator_infra.entity.Analise;
import br.com.vrinteriorpaulista.validator_infra.entity.Equipamento;
import br.com.vrinteriorpaulista.validator_infra.entity.Levantamento;
import br.com.vrinteriorpaulista.validator_infra.repository.LevantamentoRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class RelatorioService {

    private final LevantamentoRepository levantamentoRepo;

    public RelatorioService(LevantamentoRepository levantamentoRepo) {
        this.levantamentoRepo = levantamentoRepo;
    }

    @Transactional(readOnly = true)
    public RelatorioLevantamentoDTO gerar(Long levantamentoId) {
        Levantamento l = levantamentoRepo.findByIdComEquipamentosEAnalises(levantamentoId)
                .orElseThrow(() -> new EntityNotFoundException("Levantamento não encontrado"));

        var resumoDim = new RelatorioLevantamentoDTO.ResumoDimensionamento(
                l.getQtdServidores(), l.getQtdPdvs(),
                l.getQtdRetaguardas(), l.getQtdConsultaPreco()
        );

        int atende = 0, naoAtende = 0, requerAnalise = 0, pendente = 0;
        List<RelatorioLevantamentoDTO.EquipamentoDetalhe> detalhes = new ArrayList<>();

        for (Equipamento e : l.getEquipamentos()) {
            Analise a = e.getAnalise();

            switch (e.getStatus()) {
                case ATENDE -> atende++;
                case NAO_ATENDE -> naoAtende++;
                case REQUER_ANALISE -> requerAnalise++;
                default -> pendente++;
            }

            RelatorioLevantamentoDTO.AnaliseResumo resumoAnalise = null;
            if (a != null) {
                resumoAnalise = new RelatorioLevantamentoDTO.AnaliseResumo(
                        a.getFabricante(), a.getModelo(), a.getCpuModelo(),
                        a.getCpuGeracao(), a.getRamGb(), a.getSoNome(),
                        a.getConfiancaGlobal()
                );
            }

            detalhes.add(new RelatorioLevantamentoDTO.EquipamentoDetalhe(
                    e.getId(),
                    e.getCategoria().name(),
                    e.getFuncao().name(),
                    e.getSequencia(),
                    e.getStatus().name(),
                    a != null ? a.getResultado().name() : null,
                    a != null ? a.getJustificativa() : null,
                    resumoAnalise
            ));
        }

        var resumoResult = new RelatorioLevantamentoDTO.ResumoResultados(
                l.getEquipamentos().size(), atende, naoAtende, requerAnalise, pendente
        );

        return new RelatorioLevantamentoDTO(
                l.getId(),
                l.getCliente().getId(),
                l.getCliente().getRazaoSocial(),
                l.getCliente().getCnpj(),
                l.getUsuario().getNome(),
                l.getCriadoEm(),
                resumoDim,
                resumoResult,
                detalhes
        );
    }
}
