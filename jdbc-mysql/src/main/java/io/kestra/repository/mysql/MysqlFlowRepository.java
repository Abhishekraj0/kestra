package io.kestra.repository.mysql;

import io.kestra.core.models.SearchResult;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.jdbc.repository.AbstractFlowRepository;
import io.micronaut.context.ApplicationContext;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.*;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Singleton
@MysqlRepositoryEnabled
public class MysqlFlowRepository extends AbstractFlowRepository {
    @Inject
    public MysqlFlowRepository(ApplicationContext applicationContext) {
        super(new MysqlRepository<>(Flow.class, applicationContext), applicationContext);
    }

    @SuppressWarnings("unchecked")
    private <R extends Record> SelectConditionStep<R> fullTextSelect(DSLContext context, List<Field<Object>> field) {
        ArrayList<Field<Object>> fields = new ArrayList<>(Collections.singletonList(DSL.field("value")));

        if (field != null) {
            fields.addAll(field);
        }

        return (SelectConditionStep<R>) context
            .select(fields)
            .hint("SQL_CALC_FOUND_ROWS")
            .from(lastRevision(false))
            .join(jdbcRepository.getTable().as("ft"))
            .on(
                DSL.field("ft.key").eq(DSL.field("rev.key"))
                    .and(DSL.field("ft.revision").eq(DSL.field("rev.revision")))
            )
            .where(this.defaultFilter());
    }

    public ArrayListTotal<Flow> find(String query, Pageable pageable) {
        return this.jdbcRepository
            .getDslContext()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                SelectConditionStep<Record1<Object>> select = this.fullTextSelect(context, Collections.emptyList());

                if (query != null) {
                    select.and(this.jdbcRepository.fullTextCondition(Arrays.asList("namespace", "id"), query));
                }

                return this.jdbcRepository.fetchPage(context, select, pageable);
            });

    }

    @Override
    public ArrayListTotal<SearchResult<Flow>> findSourceCode(String query, Pageable pageable) {
        return this.jdbcRepository
            .getDslContext()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                SelectConditionStep<Record> select = this.fullTextSelect(context, Collections.singletonList(DSL.field("source_code")));

                if (query != null) {
                    select.and(this.jdbcRepository.fullTextCondition(Collections.singletonList("source_code"), query));
                }

                return this.jdbcRepository.fetchPage(
                    context,
                    select,
                    pageable,
                    record -> new SearchResult<>(
                        this.jdbcRepository.map(record),
                        this.jdbcRepository.fragments(query, record.getValue("source_code", String.class))
                    )
                );
            });
    }
}