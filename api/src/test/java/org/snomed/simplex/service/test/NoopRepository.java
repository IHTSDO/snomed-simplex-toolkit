package org.snomed.simplex.service.test;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.RefreshPolicy;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.Optional;

public class NoopRepository<T> implements ElasticsearchRepository<T, String> {
	@Override
	public Page<T> searchSimilar(T entity, String[] fields, Pageable pageable) {
		return null;
	}

	@Override
	public <S extends T> S save(S entity, RefreshPolicy refreshPolicy) {
		return null;
	}

	@Override
	public <S extends T> Iterable<S> saveAll(Iterable<S> entities, RefreshPolicy refreshPolicy) {
		return null;
	}

	@Override
	public void deleteById(String s, RefreshPolicy refreshPolicy) {

	}

	@Override
	public void delete(T entity, RefreshPolicy refreshPolicy) {

	}

	@Override
	public void deleteAllById(Iterable<? extends String> strings, RefreshPolicy refreshPolicy) {

	}

	@Override
	public void deleteAll(Iterable<? extends T> entities, RefreshPolicy refreshPolicy) {

	}

	@Override
	public void deleteAll(RefreshPolicy refreshPolicy) {

	}

	@Override
	public <S extends T> S save(S entity) {
		return null;
	}

	@Override
	public <S extends T> Iterable<S> saveAll(Iterable<S> entities) {
		return null;
	}

	@Override
	public Optional<T> findById(String s) {
		return Optional.empty();
	}

	@Override
	public boolean existsById(String s) {
		return false;
	}

	@Override
	public Iterable<T> findAll() {
		return null;
	}

	@Override
	public Iterable<T> findAllById(Iterable<String> strings) {
		return null;
	}

	@Override
	public long count() {
		return 0;
	}

	@Override
	public void deleteById(String s) {

	}

	@Override
	public void delete(T entity) {

	}

	@Override
	public void deleteAllById(Iterable<? extends String> strings) {

	}

	@Override
	public void deleteAll(Iterable<? extends T> entities) {

	}

	@Override
	public void deleteAll() {

	}

	@Override
	public Iterable<T> findAll(Sort sort) {
		return null;
	}

	@Override
	public Page<T> findAll(Pageable pageable) {
		return null;
	}
}
