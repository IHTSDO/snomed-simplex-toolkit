package com.snomed.simplextoolkit.service;

import com.snomed.simplextoolkit.domain.RefsetMemberIntent;
import com.snomed.simplextoolkit.exceptions.ServiceException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class RefsetToolSubsetReader implements SubsetUploadProvider {

	private final InputStream inputStream;

	public RefsetToolSubsetReader(InputStream inputStream) {
		this.inputStream = inputStream;
	}

	@Override
	public List<RefsetMemberIntent> readUpload() throws ServiceException {
		List<RefsetMemberIntent> members = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
			String header = reader.readLine();
			if (header == null || !header.startsWith("id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId")) {
				throw new ServiceException("Input file does not match the expected format. Header line is incorrect.");
			}
			String line;
			while ((line = reader.readLine()) != null) {
				String[] split = line.split("\\t");
				if (split.length > 5 && split[2].equals("1")) {
					members.add(new RefsetMemberIntent(split[5]));
				}
			}
		} catch (IOException e) {
			throw new ServiceException("Failed to read input file.", e);
		}
		return members;
	}

}
